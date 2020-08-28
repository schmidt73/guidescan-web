(defproject guidescan-web "2.0"
  :description "Version 2.0 of the Guidescan website."
  :url "http://guidescan.com/"
  :license {:name "Unknown"
            :url "Unkown"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [com.github.samtools/htsjdk "2.23.0"]
                 [com.stuartsierra/component "1.0.0"]
                 [http-kit "2.4.0"]
                 [compojure "1.6.2"]
                 [ring/ring-defaults "0.3.2"]
                 [ring "1.8.1"]]
  :repl-options {:init-ns guidescan-web.core}
  :profiles {:dev {:resource-paths ["test/resources"]}
             :user {:dependencies [[hashp/hashp "0.2.0"]
                                   [reloaded.repl "0.2.4"]]}})
  
