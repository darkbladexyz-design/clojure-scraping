(ns demo.hickory-fab
  (:require [org.httpkit.client :as http]
            [clojure.data.json :as json]
            [hickory.core :as h]
            [hickory.select :as s]
            [clojure.string :as str])
  (:import (java.net URLEncoder)))

(def ua "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome Safari")

(defn url-encode
  "URL encode a string using %20 for spaces (proper URL encoding) instead of + (form encoding)"
  [s]
  (-> s
      (URLEncoder/encode "UTF-8")
      (str/replace "+" "%20")))

(defn fetch-render-json [appid market-hash-name]
  (let [u (format (str "https://steamcommunity.com/market/listings/%s/%s/"
                       "render?start=0&count=10&language=english&currency=1")
                  appid (url-encode market-hash-name))
        {:keys [status body error]} @(http/get u {:headers {"User-Agent" ua}})]
    (cond
      error            (throw (ex-info "HTTP error" {:cause error}))
      (not= 200 status) (throw (ex-info "Non-200 response" {:status status}))
      :else            (json/read-str body :key-fn keyword))))

;; Flatten *all* assets across appids and contexts (Steam can vary)
(defn collect-assets [j]
  (for [[appid app-map] (:assets j)
        [ctx  ctx-map]  app-map
        [_aid asset]    ctx-map]
    (assoc asset :_appid appid :_ctx ctx)))

(defn pick-asset [assets wanted]
  (let [wanted-lc (str/lower-case wanted)]
    (or
     ;; exact market_hash_name match
     (first (filter #(= wanted (:market_hash_name %)) assets))
     ;; contains in market_name
     (first (filter #(str/includes? (-> (:market_name %) str/lower-case) wanted-lc) assets))
     ;; anything that looks like a TF2 recipe/fabricator
     (first (filter #(or (str/includes? (str/lower-case (or (:type %) "")) "recipe")
                         (str/includes? (str/lower-case (or (:market_name %) "")) "fabricator"))
                    assets))
     ;; fallback: just take first
     (first assets))))

;; Build the same HTML structure the hover panel uses, then parse with Hickory
(defn build-hover-html [asset]
  (let [items (for [{:keys [value]} (:descriptions asset)]
                (format "<div class='descriptor'>%s</div>" value))]
    (str "<div id='largeiteminfo_item_descriptors'>"
         (apply str items) "</div>")))

(defn node-text [node]
  (->> node (tree-seq #(or (map? %) (sequential? %))
                      #(if (map? %) (:content %) %))
       (filter string?) (apply str) str/trim))

(defn parse-descriptor-lines-hickory [asset]
  (let [html (build-hover-html asset)
        hdoc (-> html h/parse h/as-hickory)]
    (->> (s/select (s/class "descriptor") hdoc)
         (map node-text)
         (remove #(= % "\u00A0"))
         (remove str/blank?) vec)))

(defn parse-req [s]
  (when-let [[_ item n] (re-matches #"(.*)\s+x\s+(\d+)$" s)]
    {:item (str/trim item) :qty (Long/parseLong n)}))

(defn extract-fabricator [asset]
  (let [name   (or (:market_name asset) (:name asset))
        itype  (:type asset)
        lines  (parse-descriptor-lines-hickory asset)
        after-in  (->> lines (drop-while #(not (str/starts-with? % "The following are the inputs"))) rest)
        [in-lines out-sec] (split-with #(not (str/starts-with? % "You will receive")) after-in)
        out-lines (rest out-sec)]
    {:name name
     :type itype
     :inputs  (->> in-lines (keep parse-req) vec)
     :outputs (->> out-lines
                   (remove #(re-find #"\(Sheen:" %)) vec)
     :raw-lines lines}))

;; extract-fabricator is used by hickory_page.clj to parse Steam kit data
