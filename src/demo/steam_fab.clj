(ns demo.steam-fab
  (:require [org.httpkit.client :as http]
            [clojure.data.json :as json]
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
      error        (throw (ex-info "HTTP error" {:cause error}))
      (not= 200 status) (throw (ex-info "Non-200 response" {:status status}))
      :else        (json/read-str body :key-fn keyword))))

(defn all-assets [render-json appid]
  ;; assets -> "440" -> "2" -> {asset-id -> asset}
  (->> (get-in render-json [:assets (str appid)])
       vals                       ; contexts
       (mapcat vals)))            ; asset maps -> asset values

(defn find-asset [render-json appid market-hash-name]
  (let [assets (all-assets render-json appid)
        exact  (first (filter #(= market-hash-name (:market_hash_name %)) assets))]
    (or exact
        ;; fallback: fuzzy match by market_name containing the first few words
        (let [needle (-> market-hash-name str/lower-case)]
          (first (filter #(str/includes? (-> (:market_name %) str/lower-case) needle) assets))))))

(defn descriptor-lines [asset]
  (->> (:descriptions asset)
       (map :value)
       (map #(-> %
                 (str/replace #"<br\s*/?>" "\n")
                 (str/replace #"&nbsp;" " ")
                 (str/replace #"<[^>]+>" "")
                 (str/replace #"\s+" " ")
                 str/trim))
       (remove str/blank?)
       vec))

(defn parse-req [s]
  ;; e.g. "Battle-Worn Robot KB-808 x 8"
  (when-let [[_ item n] (re-matches #"(.*)\s+x\s+(\d+)$" s)]
    {:item (str/trim item) :qty (Long/parseLong n)}))

(defn extract-fabricator [asset]
  (let [name   (or (:market_name asset) (:name asset))
        itype  (:type asset)
        lines  (descriptor-lines asset)
        after-in (->> lines (drop-while #(not (str/starts-with? % "The following are the inputs"))) rest)
        [in-lines out-sec] (split-with #(not (str/starts-with? % "You will receive")) after-in)
        out-lines (rest out-sec)]
    {:name name
     :type itype
     :inputs  (->> in-lines (keep parse-req) vec)
     :outputs (->> out-lines
                   (remove #(re-find #"\(Sheen:" %))  ; tweak filters as needed
                   vec)
     :raw-lines lines}))

(defn -main [& [item]]
  (let [appid 440
        item  (or item "Specialized Killstreak Rocket Launcher Kit Fabricator")
        j     (fetch-render-json appid item)
        asset (find-asset j appid item)]
    (if-not asset
      (do (println "Could not find asset for:" item)
          (System/exit 1))
      (let [fab (extract-fabricator asset)]
        (println "OK →" (:name fab) "| inputs:" (count (:inputs fab)) "| outputs:" (count (:outputs fab)))
        ;; optional: write CSV for inputs
        (with-open [w (io/writer "inputs.csv")]
          (csv/write-csv w (cons ["item" "qty"] (map (juxt :item :qty) (:inputs fab)))))
        ;; optional: dump full map for your app
        (spit "out.edn" (pr-str fab))
        ;; show lines if you’re debugging
        (doseq [l (:raw-lines fab)] (println " •" l))))))
