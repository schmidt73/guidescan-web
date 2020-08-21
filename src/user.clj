(ns user
  (:require [reloaded.repl :refer [system init start stop go reset reset-all]]
            [guidescan-web.core :refer [core-system]]))

(reloaded.repl/set-init! #(core-system))

