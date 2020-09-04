(ns user
  (:require [guidescan-web.core]
            [reload]))

(alter-var-root #'reload/init (constantly guidescan-web.core/core-system))


