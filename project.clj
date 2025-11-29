(defproject clojure-scraping "0.1.0-SNAPSHOT"
  :description "TF2 Fabricator Kit Scraper"
  :dependencies [[org.clojure/clojure "1.11.3"]
                 [http-kit "2.8.0"]
                 [hickory "0.7.1"]
                 [org.clojure/data.json "2.5.0"]
                 [org.clojure/data.csv "1.0.1"]
                 [ring/ring-core "1.10.0"]
                 [ring/ring-jetty-adapter "1.10.0"]
                 [hiccup "1.0.5"]]
  :source-paths ["src"]
  :main app.server
  :profiles {:uberjar {:aot :all}})
