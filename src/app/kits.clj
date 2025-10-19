(ns app.kits
  (:require
    [org.httpkit.client :as http]
    [hickory.core :as h]
    [hickory.select :as s]
    [clojure.string :as str]
    [clojure.data.json :as json]))

(def UA "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome Safari")

(defn fetch-html [url]
  (let [{:keys [status body error]}
        @(http/get url {:headers {"User-Agent" UA "Accept-Language" "en-US,en;q=0.9"}})]
    (when error (throw (ex-info "HTTP error" {:cause error})))
    (when (not= 200 status) (throw (ex-info "Non-200" {:status status})))
    body))

(defn node-text [node]
  (->> node
       (tree-seq #(or (map? %) (sequential? %))
                 #(if (map? %) (:content %) %))
       (filter string?)
       (apply str)
       str/trim))

(defn extract-json-object-after [html var-name]
  (when-let [pos (str/index-of html (str "var " var-name " ="))]
    (let [from (.indexOf html "{" pos)
          n    (count html)]
      (when (pos? from)
        (loop [i (inc from), depth 1]
          (when (< i n)
            (let [ch (.charAt html i)
                  d  (cond (= ch \{) (inc depth)
                           (= ch \}) (dec depth)
                           :else depth)]
              (if (zero? d)
                (subs html from (inc i))
                (recur (inc i) d)))))))))

(defn assets-from-html [html]
  (when-let [obj (extract-json-object-after html "g_rgAssets")]
    (let [data (json/read-str obj)] ; string keys
      (for [[_appid app] data
            [_ctx ctx]   app
            [_id  a]     ctx]
        a))))

(defn descriptor-lines-from-asset [asset]
  (->> (get asset "descriptions")
       (map #(get % "value"))
       (map #(-> % (str/replace #"\s+" " ") str/trim))
       (remove str/blank?)
       vec))

(defn split-in-out [lines]
  (let [after-in  (->> lines (drop-while #(not (re-find #"The following are the inputs" %))) rest)
        [ins outs] (split-with #(not (re-find #"You will receive" %)) after-in)]
    {:inputs  (->> ins
                   (keep #(when-let [[_ it n] (re-matches #"(.*)\s+x\s+(\d+)$" %)]
                            {:item (str/trim it) :qty (Long/parseLong n)}))
                   vec)
     :outputs (->> (rest outs)
                   (remove #(re-find #"\(Sheen:" %))
                   vec)}))

(defn descriptor-lines [html]
  (let [doc    (-> html h/parse h/as-hickory)
        by-cl  (s/select (s/descendant (s/class "item_desc_descriptors")
                                       (s/class "descriptor")) doc)
        by-id  (s/select (s/descendant (s/id "largeiteminfo_item_descriptors")
                                       (s/class "descriptor")) doc)
        lines  (->> (concat by-cl by-id) (map node-text)
                    (remove #(= % "\u00A0")) (remove str/blank?) vec)]
    (if (seq lines)
      lines
      (if-let [a (first (assets-from-html html))]
        (descriptor-lines-from-asset a)
        []))))

(defn kit-url [name-or-url]
  (if (and name-or-url (clojure.string/starts-with? name-or-url "http"))
    name-or-url
    (let [raw (java.net.URLDecoder/decode (or name-or-url "") "UTF-8")]
      (str "https://steamcommunity.com/market/listings/440/"
           (java.net.URLEncoder/encode raw "UTF-8")))))

(defn fetch-kit
  "Returns {:inputs [{:item :qty} ...] :outputs [..]} for the given kit."
  [name-or-url]
  (let [html   (fetch-html (kit-url name-or-url))
        lines  (descriptor-lines html)]
    (split-in-out lines)))
