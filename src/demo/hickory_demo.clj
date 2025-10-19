(ns demo.hickory-demo
  (:require
   [org.httpkit.client :as http]
   [hickory.core :as h]
   [hickory.select :as s]
   [clojure.string :as str]
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]

   ;; UNCOMMENT the next line if you use the /render endpoint (and added data.json to deps.edn)
    [clojure.data.json :as json]
   ))

;; ---------- HTTP ----------
(def ua "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome Safari")

(defn fetch-html [url]
  (let [{:keys [status body error]} @(http/get url {:headers {"User-Agent" ua}})]
    (cond
      error (throw (ex-info "HTTP error" {:cause error}))
      (not= 200 status) (throw (ex-info "Non-200 response" {:status status}))
      :else body)))

;; ---------- URL builders ----------
(import java.net.URLEncoder java.net.URI)

(defn market-url [appid market-hash-name]
  (str "https://steamcommunity.com/market/listings/"
       appid "/"
       (URLEncoder/encode market-hash-name "UTF-8")))

(defn fetch-market-html [appid market-hash-name]
  (fetch-html (market-url appid market-hash-name)))

;; Alternative for JS-rendered pages: Steam “render” endpoint.
;; UNCOMMENT if needed (and require data.json above).
(defn fetch-render-html [appid market-hash-name]
    (let [u (format (str "https://steamcommunity.com/market/listings/%s/%s/"
                         "render?start=0&count=10&language=english&currency=1")
                    appid (URLEncoder/encode market-hash-name "UTF-8"))
          body (fetch-html u)]
      (:results_html (json/read-str body :key-fn keyword))))

;; ---------- Hickory helpers ----------
(defn ->hickory [html] (-> html h/parse h/as-hickory))

(defn node-text [node]
  (->> node
       (tree-seq #(or (map? %) (sequential? %)) #(if (map? %) (:content %) %))
       (filter string?) (apply str) str/trim))

(defn descriptor-lines [hdoc]
  (->> (s/select (s/descendant (s/id "largeiteminfo_item_descriptors")
                               (s/class "descriptor"))
                 hdoc)
       (map node-text)
       (remove #(= % "\u00A0"))
       (remove str/blank?)
       vec))

(defn parse-req [s]
  ;; e.g. "Battle-Worn Robot Taunt Processor x 11"
  (when-let [[_ item n] (re-matches #"(.*)\s+x\s+(\d+)$" s)]
    {:item (str/trim item) :qty (Long/parseLong n)}))

(defn extract-fabricator [base-url hdoc]
  (let [name      (node-text (first (s/select (s/id "largeiteminfo_item_name") hdoc)))
        game      (node-text (first (s/select (s/id "largeiteminfo_game_name") hdoc)))
        item-type (node-text (first (s/select (s/id "largeiteminfo_item_type") hdoc)))
        lines     (descriptor-lines hdoc)
        after-in  (->> lines
                       (drop-while #(not (str/starts-with? % "The following are the inputs")))
                       rest)
        [in-lines out-sec] (split-with #(not (str/starts-with? % "You will receive")) after-in)
        out-lines          (rest out-sec)]
    {:name name
     :game game
     :type item-type
     :inputs  (->> in-lines (keep parse-req) vec)
     :outputs (->> out-lines (remove #(re-find #"\(Sheen:" %)) vec)}))

;; ---------- CLI entry (URL adjustable via arg) ----------
(defn -main
  "Run with e.g.:
     clj -M -m demo.hickory-demo \"Specialized Killstreak Rocket Launcher Kit Fabricator\""
  [& [market-hash-name]]
  (let [item  (or market-hash-name "Specialized Killstreak Rocket Launcher Kit Fabricator")
        appid 440
        ;; choose ONE fetch path:
        html  (fetch-market-html appid item)
        ;; html  (fetch-render-html appid item) ; <- use this if plain HTML returns empty
        hdoc  (->hickory html)
        data  (extract-fabricator (market-url appid item) hdoc)]
    ;; Write a quick CSV of inputs (handy for you today)
    (with-open [w (io/writer "inputs.csv")]
      (csv/write-csv w (cons ["item" "qty"] (map (juxt :item :qty) (:inputs data)))))
    ;; And a machine-friendly dump for your future web backend
    (spit "out.edn" (pr-str data))
    (println "OK →" item "| inputs:" (count (:inputs data)) "| outputs:" (count (:outputs data)))))
