(defproject guidescan-web "2.0.3"
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
                 [clj-http "3.10.3"]
                 [com.taoensso/timbre "4.10.0"]
                 [org.clojure/tools.cli "1.0.194"]
                 [seancorfield/next.jdbc "1.1.610"]
                 [honeysql "1.0.444"]
                 [dk.ative/docjure "1.14.0"]
                 [com.h2database/h2 "1.4.197"]
                 [org.postgresql/postgresql "42.2.5"]
                 [com.mchange/c3p0 "0.9.5.5"]
                 [ring "1.8.1"]]
  :profiles {:uberjar {:aot :all}
             :dev {:resource-paths ["test/resources"]
                   :source-paths ["src" "dev" "test"]
                   :repl-options {:init-ns reload}
                   :dependencies [[org.clojure/tools.namespace "1.0.0"]]}
             :prod {:main guidescan-web.core
                    :jar-name "guidescan.jar-THIN"
                    :uberjar-name "guidescan.jar"}
             ;;;; Scripts for various maintenance tasks
             :database-generation {:source-paths ^:replace ["scripts"]
                                   :uberjar-name "db-gen.jar"
                                   :jar-name "db-gen.jar-THIN"}})
  
