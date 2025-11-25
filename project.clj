(defproject clojure-scraping "0.1.0-SNAPSHOT"
  :description "TF2 Fabricator Helper - Steam Market scraper for TF2 kit fabricators"
  :url "https://github.com/darkbladexyz-design/clojure-scraping"
  :license {:name "MIT"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.11.3"]
                 [http-kit "2.8.0"]
                 [hickory "0.7.1"]
                 [org.clojure/data.json "2.5.0"]
                 [ring/ring-core "1.11.0"]
                 [ring/ring-jetty-adapter "1.11.0"]
                 [etaoin "1.0.40"]]
  :source-paths ["src"]
  :main app.server
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
