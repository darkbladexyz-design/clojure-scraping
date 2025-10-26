(ns app.server
  (:require
   [ring.adapter.jetty :as jetty]
   [ring.util.response :as resp]
   [ring.middleware.params :refer [wrap-params]]
   [clojure.data.json :as json] 
   [clojure.walk :as walk]
   [app.kits :as kits]
   [demo.hickory-page :as hp]))           ;; <-- dash, and we just use hp/fetch-kit

;; ---- HTML page -------------------------------------------------------------

(defn index-page []
  (str
   "<!doctype html><meta charset=utf-8>"
   "<h1>TF2 Fabricator Helper</h1>"
   "<label>Select kit: "
   "<input id='kit' value='Specialized Killstreak Rocket Launcher Kit Fabricator'>"
   "</label>"
   " <button id='go'>Fetch</button>"
   "<pre id='out'>Loading...</pre>"
   "<script>"
   "const go=document.getElementById('go');"
   "const sel=document.getElementById('kit');"
   "const out=document.getElementById('out');"
   "async function run(){"
   "  try {"
   "    out.textContent='Loading...';"
   "    console.log('Sending request for:', sel.value);"
   "    const r=await fetch('/api/kit?name='+encodeURIComponent(sel.value));"
   "    if (!r.ok) {"
   "      throw new Error(`HTTP ${r.status}: ${await r.text()}`);"
   "    }"
   "    const data=await r.json();"
   "    console.log('Got response:', data);"
   "    out.textContent=JSON.stringify(data,null,2);"
   "  } catch (e) {"
   "    console.error('Error:', e);"
   "    out.textContent = 'Error: ' + e.message;"
   "  }"
   "}"
   "go.onclick=run; run();"
   "</script>"))

;; ---- JSON API --------------------------------------------------------------

(defn api-handler [req]
  (try
    (println "\n=== Starting API request ===")
    (println "Request:" (pr-str req))
    (let [nm  (or (get-in req [:query-params "name"])
                  (get-in req [:params :name]))
          _ (println "\nHandling request for kit:" nm)
          _ (when (nil? nm) 
              (throw (ex-info "No kit name provided" {:status 400})))
          _ (println "Calling name->url with:" nm)
          url (try
                (kits/name->url nm)
                (catch Exception e
                  (println "Error in name->url:" (pr-str e))
                  (println "Stack trace:")
                  (.printStackTrace e)
                  (throw e)))
          _ (println "Generated URL:" url)
          kit (try 
                (println "Fetching kit data from URL...")
                (hp/fetch-kit url)
                (catch Exception e
                  (println "Error fetching kit:" (.getMessage e))
                  (println "Stack trace:")
                  (.printStackTrace e)
                  (throw (ex-info "Failed to fetch kit data" {:status 500 :url url :error (.getMessage e)}))))
          _ (println "Kit data:" (pr-str kit))
          ;; make the whole thing JSON-safe (no keyword values)
          json-safe (walk/postwalk (fn [x] (if (keyword? x) (name x) x))
                                   (merge {:name nm :url url} kit))]
      (-> (json/write-str json-safe)
          resp/response
          (resp/content-type "application/json")
          (assoc-in [:headers "Access-Control-Allow-Origin"] "*")))
    (catch Throwable t
      (println "\n=== Error in API handler ===")
      (println "Error type:" (class t))
      (println "Message:" (.getMessage t))
      (println "Data:" (pr-str (ex-data t)))
      (println "Stack trace:")
      (.printStackTrace t)
      (let [data (ex-data t)
            status (or (:status data) 500)
            error-details {:error (.getMessage t)
                         :type (str (class t))
                         :details data
                         :stacktrace (map str (.getStackTrace t))}]
        (println "Sending error response:" (pr-str error-details))
        (-> error-details
            ;; also make errors safe, just in case a keyword sneaks in
            (walk/postwalk (fn [x] (if (keyword? x) (name x) x)))
            json/write-str
            resp/response
            (resp/status status)
            (resp/content-type "application/json")
            (assoc-in [:headers "Access-Control-Allow-Origin"] "*"))))))


;; ---- Router ---------------------------------------------------------------

(defn handler [req]
  (println "\nIncoming request:" (:uri req))
  (flush)
  (case (:uri req)
    "/"        (do
                 (println "Serving index page")
                 (flush)
                 (-> (index-page)
                     resp/response
                     (resp/content-type "text/html")))
    "/api/kit" (do
                 (println "Handling kit request")
                 (flush)
                 (api-handler req))
    "/api/test" (do
                  (println "Test endpoint called")
                  (flush)
                  (-> {:status "ok" :time (str (java.time.LocalDateTime/now))}
                      json/write-str
                      resp/response
                      (resp/content-type "application/json")))
    (do
      (println "404 for URI:" (:uri req))
      (flush)
      (-> "Not found" resp/response (resp/status 404)))))

(def app
  ;; parse query params into req :query-params (string keys)
  (wrap-params handler))

;; ---- Lifecycle ------------------------------------------------------------

(defonce server* (atom nil))

(defn start! [port]
  (when @server*
    (.stop ^org.eclipse.jetty.server.Server @server*))
  (reset! server* (jetty/run-jetty app {:port port :join? false}))
  (println "Server on http://localhost:" port))

(defn stop! []
  (when @server* (.stop ^org.eclipse.jetty.server.Server @server*))
  (reset! server* nil)
  (println "Server stopped"))

(defn -main [& _]
  (println "\n=== Starting server with debug logging enabled ===")
  (.addShutdownHook (Runtime/getRuntime) 
                    (Thread. #(println "\nShutting down server...")))
  (start! 3000)
  (println "Server ready. Try accessing:")
  (println "1. Main page: http://localhost:3000")
  (println "2. Test endpoint: http://localhost:3000/api/test")
  (flush))

;; Force standard out to flush immediately
(System/setProperty "java.util.logging.SimpleFormatter.format" 
                   "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$s %2$s %5$s%6$s%n")
(.setLevel (java.util.logging.Logger/getLogger "")
           java.util.logging.Level/ALL)
