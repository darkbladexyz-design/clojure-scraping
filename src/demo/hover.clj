(ns demo.hover
  (:require [etaoin.api :as e]
            [hickory.core :as h]
            [hickory.select :as s]
            [clojure.string :as str]))

;; ------- utilities that adapt to your Etaoin version -----------------------

(defn try-call
  "Try calling fully-qualified symbol `sym` with args. Returns nil if missing."
  [sym & args]
  (try
    (when-let [f (requiring-resolve sym)] (apply f args))
    (catch Throwable _ nil)))

(defn hover! [dr q]
  ;; Try several possible function names; first one that exists wins.
  (or (try-call 'etaoin.api/hover dr q)
      (try-call 'etaoin.api/move-to dr q)
      (try-call 'etaoin.api/mouse-over dr q)))

(defn js! [dr code]
  ;; Different Etaoin versions name this differently.
  (or (try-call 'etaoin.api/js-execute dr code)
      (try-call 'etaoin.api/execute dr code)
      (try-call 'etaoin.api/js dr code)))

(defn scroll-into-view! [dr css]
  (js! dr (format
           "var el=document.querySelector('%s'); if(el){ el.scrollIntoView({block:'center'}); }"
           css)))

(defn dispatch-mouseover! [dr css]
  (js! dr (format
           "var el=document.querySelector('%s'); if(el){ el.dispatchEvent(new MouseEvent('mouseover',{bubbles:true})); }"
           css)))

(defn node-text [node]
  (->> node (tree-seq #(or (map? %) (sequential? %))
                      #(if (map? %) (:content %) %))
       (filter string?) (apply str) str/trim))

(defn parse-descriptor-lines [desc-html]
  (let [hdoc (-> (or desc-html "") h/parse h/as-hickory)]
    (->> (s/select (s/class "descriptor") hdoc)
         (map node-text)
         (remove #(= % "\u00A0"))
         (remove str/blank?)
         vec)))

(defn file-exists? [p] (.exists (java.io.File. p)))

(defn make-driver []
  ;; Use Google Chrome with chromedriver for WSL2
  (let [chrome-bin (or (System/getenv "CHROME_BIN") "/usr/bin/google-chrome")
        chromedriver-bin (or (System/getenv "CHROMEDRIVER_PATH") "/usr/local/bin/chromedriver")]
    (println "hover: attempting chrome/chromedriver..." chrome-bin chromedriver-bin)
    (when-not (file-exists? chrome-bin)
      (println "hover: chrome binary not found at" chrome-bin))
    (when-not (file-exists? chromedriver-bin)
      (println "hover: chromedriver not found at" chromedriver-bin))
    (e/chrome
     {:path-driver  chromedriver-bin
      :path-browser chrome-bin
      :headless true
      :args ["--no-sandbox" "--disable-dev-shm-usage" "--disable-gpu" "--window-size=1400,1000"]})))

(def img-css  "#searchResultsRows .market_listing_row .market_listing_item_img.economy_item_hoverable")
(def link-css "#searchResultsRows .market_listing_row .market_listing_item_name_link")

(defn panel-html [dr]
  (when (e/visible? dr {:id "largeiteminfo_content"})
    (e/get-element-attr dr {:id "largeiteminfo_item_descriptors"} "innerHTML")))

(defn hover-and-grab [dr css]
  (scroll-into-view! dr css)
  (hover! dr {:css css})
  (Thread/sleep 600)
  (or (panel-html dr)
      (do (dispatch-mouseover! dr css)
          (Thread/sleep 600)
          (panel-html dr))))

(defn wait-visible*
  "Simple replacement for etaoin's wait-visible which avoids internal
  etaoin/wait incompatibilities across versions. Returns true if selector
  becomes visible within timeout-seconds, nil otherwise."
  [dr selector timeout-seconds]
  (let [deadline (+ (System/currentTimeMillis) (* 1000 (or timeout-seconds 10)))]
    (loop []
      (let [now (System/currentTimeMillis)]
        (if (< now deadline)
          (let [ok (try (e/visible? dr selector) (catch Throwable _ false))]
            (if ok
              true
              (do (Thread/sleep 200) (recur))))
          nil)))))

;; ------------------------------ main ---------------------------------------

(defn -main [& [url]]
  (let [url (or url "https://steamcommunity.com/market/listings/440/Specialized%20Killstreak%20Rocket%20Launcher%20Kit%20Fabricator")
        dr  (make-driver)]
    (try
      (e/go dr url)
      (wait-visible* dr {:css "#searchResultsRows .market_listing_row"} 15)
      ;; try image first, then title link
      (let [html  (or (hover-and-grab dr img-css)
                      (hover-and-grab dr link-css))
            lines (parse-descriptor-lines html)]
        (println "descriptor lines:" (count lines))
        (doseq [l lines] (println " •" l)))
      (finally (e/quit dr)))))

(defn capture-descriptors
  "Navigate to `url`, attempt a hover and return descriptor lines (vector of strings).
  Returns nil on failure. This is a safe helper intended to be called from the
  server-side scraper as a last-resort when static parsing fails."
  [url]
  (let [dr (try (make-driver) (catch Throwable _ nil))]
    (when dr
      (try
        (e/go dr url)
        ;; wait for listing row or the hover container to be present
        (wait-visible* dr {:css "#searchResultsRows .market_listing_row"} 8)
        (let [html (or (hover-and-grab dr img-css)
                      (hover-and-grab dr link-css))]
          (when html
            (parse-descriptor-lines html)))
        (catch Throwable _ nil)
        (finally (try (e/quit dr) (catch Throwable _ nil)))))))
