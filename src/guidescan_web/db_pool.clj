(ns guidescan-web.db-pool
  (:import (com.mchange.v2.c3p0 ComboPooledDataSource))
  (:require [guidescan-web.utils :refer :all]
            [taoensso.timbre :as timbre]
            [next.jdbc :as jdbc]
            [honeysql.core :as sql]
            [honeysql.helpers :as h]
            [failjure.core :as f]
            [com.stuartsierra.component :as component]))

(defn- create-pool
  [spec]
  (let [cpds (doto (ComboPooledDataSource.)
               (.setDriverClass (:classname spec))
               (.setJdbcUrl (:jdbcUrl spec))
               (.setMaxIdleTimeExcessConnections (* 30 60))
               (.setMaxIdleTime (* 3 60 60)))]
    cpds))

(defrecord DBPool [db-pool config]
  component/Lifecycle
  (start [this]
    (let [gr-map (atom this)]
      (if-let [db-spec (:db-spec (:config config))]
        (let [pool (create-pool db-spec)]
          (timbre/info "Successfully initialized SQL DB connection pool.")
          (swap! gr-map #(assoc % :db-pool pool)))
        (timbre/warn "SQL DB not specified. Certain features will not be supported."))
     @gr-map))
  (stop [this]
    (.close db-pool)
    (assoc this :db-pool nil)))

(defn create-db-pool
  []
  (map->DBPool {}))

(defn get-db-conn
  "Gets a connection to the SQL database from the DBPool component."
  [db-pool]
  (.getConnection (:db-pool db-pool)))
