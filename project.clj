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
                 [seancorfield/next.jdbc "1.1.610"]
                 [honeysql "1.0.444"]
                 [puppetlabs/postgresql "9.2-1002.jdbc4"]
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
             :add-gene-symbols {:source-paths ^:replace ["scripts"]
                                :uberjar-name "add-gene-symbols.jar"
                                :jar-name "add-gene-symbols.jar-THIN"
                                :main add-gene-symbols}
             :create-gene-db {:source-paths ^:replace ["scripts"]
                              :uberjar-name "create-gene-db.jar"
                              :jar-name "create-gene-db.jar-THIN"
                              :main create-gene-db}
             :add-organism   {:source-paths ^:replace ["scripts"]
                              :uberjar-name "add-organism.jar"
                              :jar-name "add-organism.jar-THIN"
                              :main add-organism}})

  
