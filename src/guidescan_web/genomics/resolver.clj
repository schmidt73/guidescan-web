(ns guidescan-web.genomics.resolver
  "Defines a component that resolves various types of genomic names."
  (:import (com.mchange.v2.c3p0 ComboPooledDataSource))
  (:require [taoensso.timbre :as timbre]
            [next.jdbc :as jdbc]
            [honeysql.core :as sql]
            [honeysql.helpers :as h]
            [com.stuartsierra.component :as component]))

(defn- create-pool
  [spec]
  (let [cpds (doto (ComboPooledDataSource.)
               (.setDriverClass (:classname spec))
               (.setJdbcUrl (:jdbcUrl spec))
               (.setMaxIdleTimeExcessConnections (* 30 60))
               (.setMaxIdleTime (* 3 60 60)))]
    cpds))

(defn- create-entrez-id-query [entrez-id]
  (sql/format
   (sql/build
     :select :*
     :from :genes
     :where [:= :genes.entrez_id entrez-id])))

(defn- create-gene-symbol-query [gene-symbol]
  (sql/format
   (sql/build
     :select :*
     :from :genes
     :where [:= :genes.gene_symbol (clojure.string/lower-case gene-symbol)])))

(defrecord GeneResolver [db-pool config]
  component/Lifecycle
  (start [this]
    (timbre/info "Initializing DB connection pool.")
    (let [pool (create-pool (:db-spec (:config config)))]
      (timbre/info "Successfully initalized DB connection pool.")
      (assoc this :db-pool pool)))
  (stop [this]
    (.close db-pool)
    (assoc this :db-pool nil)))

(defn gene-resolver []
  (map->GeneResolver {}))

(defn resolve-gene-symbol [gene-resolver gene-symbol]
  (with-open [conn (.getConnection (:db-pool gene-resolver))]
    (jdbc/execute! conn (create-gene-symbol-query gene-symbol))))

(defn resolve-entrez-id [gene-resolver entrez-id]
  (with-open [conn (.getConnection (:db-pool gene-resolver))]
    (jdbc/execute! conn (create-entrez-id-query entrez-id))))
