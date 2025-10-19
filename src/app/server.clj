(ns app.server
  (:require
   [hiccup.page :as hp]
   [ring.adapter.jetty :as jetty]
   [ring.util.response :as resp]
   [ring.middleware.params :refer [wrap-params]]
   [clojure.data.json :as json]
   [clojure.walk :as walk]
   [demo.hickory-page :as scrape]))   ;; <- this is what provides fetch-kit


;; --- API: /api/kit?name=... -----------------------------------------------

(defn api-handler [{:keys [query-params]}]
  (let [name (get query-params "name")
        data (scrape/fetch-kit name)]           ;; <- this must now exist
    (-> (resp/response (json/write-str (walk/stringify-keys data)))
        (resp/content-type "application/json"))))

;; --- HTML index page -------------------------------------------------------

(defn index-page []
  (hp/html5
   [:head
    [:meta {:charset "utf-8"}]
    [:title "TF2 Fabricator Helper"]]
   [:body
    [:h1 "TF2 Fabricator Helper"]
    [:label {:for "kit"} "Select kit: "]
    [:select {:id "kit"}        ;; you populate options client-side or server-side
     [:option {:value "Specialized Killstreak Rocket Launcher Kit Fabricator"}
      "Specialized Killstreak Rocket Launcher Kit Fabricator"]]
    [:button {:id "go"} "Fetch"]
    [:pre {:id "out"}]
    [:script
     "const out=document.getElementById('out');"
     "document.getElementById('go').onclick=async()=>{"
     "  const name=document.getElementById('kit').value;"
     "  const r=await fetch('/api/kit?name='+encodeURIComponent(name));"
     "  const j=await r.json();"
     "  out.textContent=JSON.stringify(j,null,2);"
     "}"]]))

;; --- Router ---------------------------------------------------------------

(defn handler [req]
  (case (:uri req)
    "/"       (-> (resp/response (index-page))
                  (resp/content-type "text/html"))
    "/api/kit" (api-handler req)
    (-> (resp/response "Not found") (resp/status 404))))

(def app (wrap-params handler))

(defonce server* (atom nil))
(defn start! [port]
  (when @server* (.stop @server*))
  (reset! server* (jetty/run-jetty app {:port port :join? false}))
  (println "Server on http://localhost:" port))
(defn stop! [] (when @server* (.stop @server*) (reset! server* nil)))
