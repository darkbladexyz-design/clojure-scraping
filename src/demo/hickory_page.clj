(ns demo.hickory-page
  (:require [org.httpkit.client :as http]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [demo.hickory-fab :as hf]
            [demo.hover :as hover]))

(def default-headers
  {"accept" "*/*"
   "user-agent" "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"})

(defn- get!
  ([u] (get! u {}))
  ([u opts]
   (let [opts' (merge {:headers default-headers
                       :timeout 120000
                       :follow-redirects true}
                      opts)
         {:keys [status body error]} @(http/get u opts')]
     (when error
       (throw (ex-info (str "HTTP error " error) {:url u})))
     (when (not (<= 200 status 299))
       (throw (ex-info (str "HTTP " status) {:url u :status status})))
     body)))


(defn- clean-lines
  "Normalize descriptor strings: strip <br>, nbsp, trim, drop empties."
  [xs]
  (->> xs
       (map #(-> %
                 (str/replace #"<br\s*/?>" "\n")
                 (str/replace #"\u00A0|&nbsp;" " ")
                 (str/replace #"[ \t\r\f\v]+" " ")
                 (str/trim)))
       (mapcat #(str/split % #"\n"))
       (map str/trim)
       (remove str/blank?)
       vec))

(defn- split-kit
  "Given cleaned descriptor lines, split into {:inputs [...] :outputs [...] }."
  [lines]
  (let [i-h (some->> lines
                     (keep-indexed (fn [i s]
                                     (when (re-find #"(?i)inputs that must be fulfilled" s) i)))
                     first)
        o-h (some->> lines
                     (keep-indexed (fn [i s]
                                     (when (re-find #"(?i)you will receive.*outputs" s) i)))
                     first)
        end (or (some->> lines
                         (keep-indexed (fn [i s]
                                         (when (re-find #"(?i)this is a limited use item" s) i)))
                         first)
                (count lines))]
    (when (or (nil? i-h) (nil? o-h))
      (throw (ex-info "Could not extract kit descriptors from page" {:lines lines})))
    {:inputs  (subvec lines (inc i-h) o-h)
     :outputs (subvec lines (inc o-h) end)}))

;; --- strategy 1: render endpoint -------------------------------------------

 (defn- from-render [listing-url]
  ;; Use the listing-specific render endpoint ("/render?...") which reliably
  ;; returns JSON for the single listing. Some pages don't accept a
  ;; `render=1` query param on the listing URL but do support the
  ;; "/render?start=..." path.
  (let [render-url (str (if (str/ends-with? listing-url "/")
                         listing-url
                         (str listing-url "/"))
                        "render?start=0&count=1&language=english&currency=1")
        ;; Pretend to be an AJAX call from that listing page
        body (get! render-url {:headers {"X-Requested-With" "XMLHttpRequest"
                                         "Accept" "application/json, text/javascript, */*; q=0.01"
                                         "Referer" listing-url}})
        parsed (try
                 (json/read-str body :key-fn keyword)
                 (catch Exception _ ::not-json))]
    (when (not= parsed ::not-json)
    (let [hovers (:hovers parsed)
      first-hover (when (map? hovers)
            (-> hovers vals first))
      descs (some->> first-hover
            :descriptions
            (map :value)
            vec)]
        (when (seq descs)
          (-> descs clean-lines split-kit))))))


;; --- strategy 2: g_rgAssets in HTML ----------------------------------------

(defn- from-g-assets [listing-url]
  (let [html (get! listing-url)
        ;; handle `var g_rgAssets = {...};` or `g_rgAssets = {...};`
        m  (some->> html
                    (re-find #"(?s)g_rgAssets\s*=\s*(\{.*?\});")
                    second
                    (json/read-str :key-fn keyword))
        ;; walk the nested {:appid {:contextid {id {:descriptions [...]}}}}
        descs (some->> m
                       vals first         ; first appid
                       vals first         ; context "2"
                       vals first         ; first asset
                       :descriptions
                       (map :value) vec)]
    (when (seq descs)
      (-> descs clean-lines split-kit))))

;; --- strategy 3: parse descriptor divs directly from listing HTML ---------

(defn- from-html-descriptors [listing-url]
  "Parse the listing page HTML and extract any <div class=\"descriptor\"> text.
  Targets the DOM under #largeiteminfo_content -> .item_desc_descriptors."
  (let [html (get! listing-url)
        ;; find all descriptor div inner HTML (DOTALL, case-insensitive)
        matches (re-seq #"(?is)<div\s+class=\"descriptor\"[^>]*>(.*?)</div>" html)
        inner   (map second matches)
        ;; strip HTML tags and decode common entities
        strip-tags (fn [s]
                     (-> s
                         (str/replace #"(?i)<br\s*/?>" "\n")
                         (str/replace #"<[^>]+>" "")
                         (str/replace #"&nbsp;|\u00A0" " ")
                         (str/trim)))
        descs   (->> inner
                     (map strip-tags)
                     (remove str/blank?)
                     vec)]
    (when (seq descs)
      (-> descs clean-lines split-kit))))


(defn- from-render-assets [listing-url]
  "Use demo.hickory-fab helpers to fetch the listing render JSON, collect
  assets and parse descriptors from the matching asset when available."
  (when listing-url
    (let [m (some-> (re-find #"/listings/\d+/(.+)$" listing-url) second (java.net.URLDecoder/decode "UTF-8"))
          appid 440
          j (try (hf/fetch-render-json appid m) (catch Exception _ nil))
          assets (when j (vec (hf/collect-assets j)))
          asset (when (seq assets) (hf/pick-asset assets m))]
      (when asset
        (let [fab (hf/extract-fabricator asset)]
          (when (or (seq (:inputs fab)) (seq (:outputs fab)))
            {:name (:name fab)
             :url listing-url
             :inputs (:inputs fab)
             :outputs (:outputs fab)
             :raw (:raw-lines fab)}))))))

;; --- public ---------------------------------------------------------------

(defn fetch-kit
  "Return {:inputs [...] :outputs [...]} for a Steam Market listing URL."
  [listing-url]
  (or
    (from-render listing-url)
    (from-render-assets listing-url)
    (from-g-assets listing-url)
    (from-html-descriptors listing-url)
    ;; strategy 4: try headless browser hover capture (geckodriver + Firefox)
    (try
      (when-let [lines (hover/capture-descriptors listing-url)]
        (when (seq lines)
          (-> lines clean-lines split-kit)))
      (catch Throwable _ nil))
    ;; Fallback: if Steam doesn't expose kit descriptors on the listing page
    ;; (common when there are no active listings / hover data), return a
    ;; safe minimal map with the listing name and URL so callers can show a
    ;; helpful message rather than just failing with null.
    (let [m (when listing-url
              (some-> (re-find #"/listings/\d+/(.+)$" listing-url)
                      second
                      (java.net.URLDecoder/decode "UTF-8")))]
      {:name m
       :url  listing-url
       :note "No kit descriptors found on the listing; Steam did not provide hover/render descriptions."})))
