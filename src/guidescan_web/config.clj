(ns guidescan-web.config
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [taoensso.timbre :as timbre]
            [com.stuartsierra.component :as component]))

(defrecord Configuration [config-name config]
  component/Lifecycle
  (start [this]
    (when (nil? config)
      (timbre/info "Loading configuration")
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

(defn get-genome-structure
  [config organism]
  (get-in (:config config) [:genome-structure-map organism]))
