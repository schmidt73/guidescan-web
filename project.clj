(defproject guidescan-web "2.0"
  :description "Version 2.0 of the Guidescan website."
  :url "http://guidescan.com/"
  :license {:name "Unknown"
            :url "Unkown"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [com.github.samtools/htsjdk "2.23.0"]]
  :repl-options {:init-ns guidescan-web.core})
