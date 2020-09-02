(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh refresh-all disable-reload!]]
            [guidescan-web.core]
            [com.stuartsierra.component :as component]))

(disable-reload!)

(def system nil)

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system component/stop))

(defn reset-all []
  (when (some? system) (stop))
  (alter-var-root #'system (constantly (guidescan-web.core/core-system)))
  (refresh-all :after 'user/start)
  nil)
