(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh refresh-all disable-reload!]]
            [guidescan-web.core :refer [core-system]]
            [com.stuartsierra.component :as component]))

(def system (core-system))
(def state :ready)

(defn start []
  (alter-var-root #'system component/start)
  (alter-var-root #'state (constantly :started)))

(defn stop []
  (alter-var-root #'system component/stop)
  (alter-var-root #'state (constantly :stopped)))

(defn reset-all []
  (when (= :started state) (stop))
  (refresh-all :after 'user/start))
