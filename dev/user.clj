(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh refresh-all disable-reload!]]
            [com.stuartsierra.component :as component]
            [guidescan-web.core :refer [core-system]]))

(disable-reload!)

(def system nil)

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system component/stop)
  (alter-var-root #'system (constantly nil)))

(defn reset-all []
  (when (some? system) (stop))
  (alter-var-root #'system (fn [_] (core-system)))
  (refresh-all :after 'user/start))
