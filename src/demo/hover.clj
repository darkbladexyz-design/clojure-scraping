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
  (etaoin.api/chrome
   {:path-driver  "/usr/local/bin/chromedriver"   ;; <-- from `which chromedriver`
    :path-browser "/usr/bin/google-chrome"        ;; <-- or "/usr/bin/google-chrome-stable"
    :headless false
    :args ["--no-sandbox" "--disable-dev-shm-usage" "--window-size=1400,1000"]}))





(def img-css  "#searchResultsRows .market_listing_row .market_listing_item_img.economy_item_hoverable")
(def link-css "#searchResultsRows .market_listing_row .market_listing_item_name_link")

(defn panel-html [dr]
  (when (e/visible? dr {:id "largeiteminfo_content"})
    (e/get-element-attr dr {:id "largeiteminfo_item_descriptors"} "innerHTML")))

(defn hover-and-grab [dr css]
  (scroll-into-view! dr css)
  (hover! dr {:css css})
  (e/wait 0.6)
  (or (panel-html dr)
      (do (dispatch-mouseover! dr css)
          (e/wait 0.6)
          (panel-html dr))))

;; ------------------------------ main ---------------------------------------

(defn -main [& [url]]
  (let [url (or url "https://steamcommunity.com/market/listings/440/Specialized%20Killstreak%20Rocket%20Launcher%20Kit%20Fabricator")
        dr  (make-driver)]
    (try
      (e/go dr url)
      (e/wait-visible dr {:css "#searchResultsRows .market_listing_row"} 15)

      ;; try image first, then title link
      (let [html  (or (hover-and-grab dr img-css)
                      (hover-and-grab dr link-css))
            lines (parse-descriptor-lines html)]
        (println "descriptor lines:" (count lines))
        (doseq [l lines] (println " •" l)))
      (finally (e/quit dr)))))
