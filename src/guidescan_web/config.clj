(ns guidescan-web.config
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]))

(defrecord Configuration [config-name config]
  component/Lifecycle
  (start [this]
    (when (nil? config)
      (if-let [c (edn/read-string (slurp (io/resource config-name)))]
        (assoc this :config c))))
  (stop [this]
    (assoc this :config nil)))

(defn create-config [config-name]
  (map->Configuration {:config-name config-name}))

(defn get-grna-db-path [config organism]
  (.getPath
    (io/file
     (:grna-database-path-prefix (:config config))
     (get (:grna-database-path-map (:config config))
          organism))))
