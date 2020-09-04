(ns user
  (:require [guidescan-web.core]
            [clojure.java.io :as io]
            [reload]))

(alter-var-root
 #'reload/init
 (constantly #(guidescan-web.core/core-system "localhost" 8000 (io/resource "config.edn"))))


