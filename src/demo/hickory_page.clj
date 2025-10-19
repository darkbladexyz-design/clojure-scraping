(ns demo.hickory-page
  (:require [org.httpkit.client :as http]
            [hickory.core :as h]
            [hickory.select :as s]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [clojure.data.json :as json])
  (:import (java.net URLEncoder)))


(defn extract-json-object-after
  "Finds `var <var-name> = { ... };` in `html` and returns the JSON object string
   (without the trailing semicolon). Uses a simple brace counter."
  [html var-name]
  (when-let [pos (str/index-of html (str "var " var-name " ="))]
    (let [from     (.indexOf html "{" pos)
          n        (count html)]
      (when (pos? from)
        (loop [i (inc from), depth 1]
          (when (< i n)
            (let [ch (.charAt html i)
                  depth' (cond
                           (= ch \{) (inc depth)
                           (= ch \}) (dec depth)
                           :else     depth)]
              (if (zero? depth')
                ;; include braces; strip trailing ';' outside this fn
                (subs html from (inc i))
                (recur (inc i) depth')))))))))

(defn lines-from-g-rgassets
  "Extract descriptor lines from the embedded g_rgAssets JSON in the page."
  [html]
  (when-let [json-obj (extract-json-object-after html "g_rgAssets")]
    (let [json-str   json-obj                       ; object only
          data       (json/read-str json-str)       ; keep string keys
          ;; flatten all assets: data is {appid -> {ctxid -> {id -> asset}}}
          assets     (for [[_appid app] data
                           [_ctx ctx]   app
                           [_id  a]     ctx]
                       a)
          asset      (first assets)                 ; pick the first; you can pick by name if you like
          descs      (get asset "descriptions")]
      (->> descs
           (map #(get % "value"))
           (map #(-> % (str/replace #"\s+" " ") str/trim))
           (remove str/blank?)
           vec))))

(def UA "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome Safari")

(defn fetch-html [url]
  (let [{:keys [status body error]}
        @(http/get url {:headers {"User-Agent" UA
                                  "Accept-Language" "en-US,en;q=0.9"}})]
    (cond
      error             (throw (ex-info "HTTP error" {:cause error}))
      (not= 200 status) (throw (ex-info "Non-200 response" {:status status}))
      :else body)))

(defn node-text [node]
  (->> node
       (tree-seq #(or (map? %) (sequential? %))
                 #(if (map? %) (:content %) %))
       (filter string?) (apply str) str/trim))

(defn descriptor-lines [html]
  (let [doc   (-> html h/parse h/as-hickory)
        by-cl (s/select (s/descendant (s/class "item_desc_descriptors")
                                      (s/class "descriptor")) doc)
        by-id (s/select (s/descendant (s/id "largeiteminfo_item_descriptors")
                                      (s/class "descriptor")) doc)
        dom-lines (->> (concat by-cl by-id)
                       (map node-text)
                       (remove #(= % "\u00A0"))
                       (remove str/blank?)
                       vec)]
    (if (seq dom-lines)
      dom-lines
      ;; Fallback: parse embedded g_rgAssets JSON
      (or (lines-from-g-rgassets html) []))))

(defn parse-req [s]
  (when-let [[_ item n] (re-matches #"(.*)\s+x\s+(\d+)$" s)]
    {:item (str/trim item) :qty (Long/parseLong n)}))

(defn split-in-out [lines]
  (let [after-in  (->> lines
                       (drop-while #(not (re-find #"The following are the inputs" %)))
                       rest)
        [in-lines out-sec] (split-with #(not (re-find #"You will receive" %)) after-in)
        out-lines (rest out-sec)]
    {:inputs  (->> in-lines (keep parse-req) vec)
     :outputs (->> out-lines
                   (remove #(re-find #"\(Sheen:" %))
                   vec)}))

(defn url-for [name-or-url]
  (if (and name-or-url (str/starts-with? name-or-url "http"))
    name-or-url
    (str "https://steamcommunity.com/market/listings/440/"
         (URLEncoder/encode
          (or name-or-url "Specialized Killstreak Rocket Launcher Kit Fabricator")
          "UTF-8"))))

(defn -main [& [name-or-url]]
  (let [url   (url-for name-or-url)
        html  (fetch-html url)]
    ;; save the exact HTML we got so you can open it if needed
    (spit "last.html" html)

    (let [lines  (descriptor-lines html)
          {:keys [inputs outputs]} (split-in-out lines)]
      (println "OK | inputs:" (count inputs) "| outputs:" (count outputs))
      (doseq [{:keys [item qty]} inputs] (println " - " qty "x" item))
      (doseq [o outputs] (println " ->" o))
      (with-open [w (io/writer "inputs.csv")]
        (csv/write-csv w (cons ["item" "qty"] (map (juxt :item :qty) inputs)))))))
