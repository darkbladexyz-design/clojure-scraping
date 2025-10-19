(ns demo.hickory-fab
  (:require [org.httpkit.client :as http]
            [clojure.data.json :as json]
            [hickory.core :as h]
            [hickory.select :as s]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv])
  (:import (java.net URLEncoder)))

(def ua "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome Safari")

(defn fetch-render-json [appid market-hash-name]
  (let [u (format (str "https://steamcommunity.com/market/listings/%s/%s/"
                       "render?start=0&count=10&language=english&currency=1")
                  appid (URLEncoder/encode market-hash-name "UTF-8"))
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

(defn -main [& [item]]
  (let [appid 440
        item  (or item "Specialized Killstreak Rocket Launcher Kit Fabricator")
        j     (fetch-render-json appid item)
        assets (vec (collect-assets j))]
    (when (empty? assets)
      (println "No assets returned. Keys in JSON:" (keys j))
      (System/exit 1))
    (let [asset (pick-asset assets item)]
      (if-not asset
        (do (println "Could not find asset for:" item)
            (println "Example assets:" (map #(select-keys % [:market_name :market_hash_name :type]) (take 3 assets))))
        (let [fab (extract-fabricator asset)]
          (println "OK →" (:name fab)
                   "| inputs:" (count (:inputs fab))
                   "| outputs:" (count (:outputs fab)))
          ;; optional files for you
          (with-open [w (io/writer "inputs.csv")]
            (csv/write-csv w (cons ["item" "qty"] (map (juxt :item :qty) (:inputs fab)))))
          (spit "out.edn" (pr-str fab))
          (doseq [l (:raw-lines fab)] (println " •" l)))))))
