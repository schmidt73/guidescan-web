(ns guidescan-web.config
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [taoensso.timbre :as timbre]
            [com.stuartsierra.component :as component]))

(defrecord Configuration [config-file config]
  component/Lifecycle
  (start [this]
    (when (nil? config)
      (timbre/info "Loading configuration")
      (if-let [c (edn/read-string (slurp config-file))]
        (assoc this :config c))))
  (stop [this]
    (assoc this :config nil)))

(defn create-config [config-file]
  (map->Configuration {:config-file config-file}))

(defn get-grna-db-path [config organism enzyme]
  (.getPath
    (io/file
     (:grna-database-path-prefix (:config config))
     (get (:grna-database-path-map (:config config))
          {:organism organism
           :enzyme enzyme}))))
