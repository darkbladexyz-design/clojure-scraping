;; src/app/kits.clj
(ns app.kits
  (:require [org.httpkit.client :as http]
            [clojure.data.json :as json]
            [clojure.pprint :as pp]
            [clojure.string :as str])
  (:import (java.net URLEncoder)))

(defn url-encode
  "URL encode a string using %20 for spaces (proper URL encoding) instead of + (form encoding)"
  [s]
  (-> s
      (URLEncoder/encode "UTF-8")
      (str/replace "+" "%20")))

(defn- http-get-json [url]
  (println "Fetching URL:" url)
  (try
    (let [options {:headers {"User-Agent" "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
                            "Accept" "application/json, text/javascript, */*; q=0.01"
                            "Referer" "https://steamcommunity.com/"}
                  :timeout 30000
                  :insecure? true  ; Allow self-signed certs
                  :follow-redirects true
                  :as :text}  ; Force response as text
          _ (println "Making request with options:" (pr-str options))
          {:keys [status body error headers] :as resp} @(http/get url options)]
      (println "Got response:")
      (println "Status:" status)
      (println "Error:" error)
      (println "Headers:" (pr-str headers))
      (println "Body preview:" (when body (subs body 0 (min 200 (count body)))))
      (when error
        (throw (ex-info (str "HTTP request failed: " error) {:url url :error error})))
      (when (not= status 200)
        (throw (ex-info "Non-200 from Steam search" {:status status :url url :body body})))
      (try
        (let [data (json/read-str body)]
          (println "Parsed JSON successfully. Keys:" (keys data))
          data)
        (catch Exception e
          (println "Failed to parse JSON. Body preview:" (when body (subs body 0 (min 200 (count body)))))
          (throw (ex-info "Failed to parse JSON response" {:url url :body-preview (when body (subs body 0 (min 200 (count body))))} e)))))
    (catch Exception e
      (println "HTTP request failed:" (.getMessage e))
      (.printStackTrace e)
      (throw (ex-info "HTTP request failed" {:url url :error (.getMessage e)} e)))))

;; Test function to check Steam connectivity
(defn- test-steam-connection []
  (println "\nTesting Steam connection...")
  (try
    (let [test-url "https://steamcommunity.com/market"
          {:keys [status]} @(http/get test-url {:timeout 10000})]
      (println "Steam connection test successful. Status:" status)
      true)
    (catch Exception e
      (println "Steam connection test failed:" (.getMessage e))
      false)))

(defn- resolve-hash-name [name]
  (println "\nTrying to resolve hash name for:" name)
  (when-not (test-steam-connection)
    (throw (ex-info "Cannot connect to Steam" {:name name})))
  (try
    (let [q   (url-encode name)
          url (str "https://steamcommunity.com/market/search/render/?appid=440&norender=1&count=50&start=0&query=" q)
          _ (println "Search URL:" url)
          data (http-get-json url)
          _ (println "Got data:" (pr-str data))
          results (get data "results")]
      (println "Found" (count results) "results")
      (doseq [r results]
        (println "Result:" (pr-str r)))
      (or
       (some #(when (= (get % "hash_name") name) (get % "hash_name")) results)
       (some-> results first (get "hash_name"))))
    (catch Exception e
      (println "Error resolving hash name:" (.getMessage e))
      (throw (ex-info "Failed to resolve hash name" {:name name :error (.getMessage e)} e)))))

(defn name->url [n]
  (println "\n=== Starting name->url ===")
  (println "Input name:" n)
  (try
    (let [hash (resolve-hash-name n)]
      (println "Resolved hash:" hash)
      (when-not hash
        (throw (ex-info "Could not resolve kit name to a listing" {:name n})))
      (let [url (format "https://steamcommunity.com/market/listings/440/%s"
                       (url-encode hash))]
        (println "Generated URL:" url)
        url))
    (catch Exception e
      (println "Error in name->url:")
      (println "Message:" (.getMessage e))
      (println "Data:" (pr-str (ex-data e)))
      (.printStackTrace e)
      (throw e))))
