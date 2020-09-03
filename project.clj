(defproject guidescan-web "2.0"
  :description "Version 2.0 of the Guidescan website."
  :url "http://guidescan.com/"
  :author "Henri Schmidt"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/data.csv "1.0.0"]
                 [com.github.samtools/htsjdk "2.23.0"]
                 [com.stuartsierra/component "1.0.0"]
                 [http-kit "2.4.0"]
                 [compojure "1.6.2"]
                 [ring/ring-defaults "0.3.2"]
                 [cheshire "5.10.0"]
                 [failjure "2.0.0"]
                 [selmer "1.12.28"]
                 [com.taoensso/timbre "4.10.0"]
                 [org.clojure/tools.cli "1.0.194"]
                 [ring "1.8.1"]]
  :main guidescan-web.core
  :profiles {:uberjar {:aot :all}
             :dev {:resource-paths ["test/resources"]
                   :source-paths ["src" "dev" "test"]
                   :repl-options {:init-ns user}
                   :dependencies [[org.clojure/tools.namespace "1.0.0"]]}})

  
