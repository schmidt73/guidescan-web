(ns reload
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :refer [refresh refresh-all disable-reload!]]))

(disable-reload!)

(def system nil)
(def init nil)

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system component/stop))

(defn reset-all []
  (when (some? system) (stop))
  (alter-var-root #'system (constantly (init)))
  (refresh-all :after 'reload/start)
  nil)

(defn reset-all-no-stop []
  (alter-var-root #'system (constantly (init)))
  (refresh-all :after 'reload/start)
  nil)
