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
    :select [:genes/entrez_id :genes/gene_symbol :genes/start_pos
             :genes/end_pos :genes/sense :chromosomes/name]
     :from [:genes :chromosomes]
     :where [:= :genes.entrez_id entrez-id]
            [:= :genes.chromosome :chromosomes/accession])))

(defn- create-gene-symbol-query [gene-symbol]
  (sql/format
   (sql/build
    :select [:genes/entrez_id :genes/gene_symbol :genes/start_pos
             :genes/end_pos :genes/sense :chromosomes/name]
     :from [:genes :chromosomes]
     :where [:= :genes.gene_symbol gene-symbol]
            [:= :genes.chromosome :chromosomes/accession]))) 

(defrecord GeneResolver [db-pool config]
  component/Lifecycle
  (start [this]
    (timbre/info "Initializing DB connection pool.")
    (let [pool (create-pool (:db-spec (:config config)))]
      (timbre/info "Successfully initialized DB connection pool.")
      (assoc this :db-pool pool)))
  (stop [this]
    (.close db-pool)
    (assoc this :db-pool nil)))

(defn gene-resolver []
  (map->GeneResolver {}))

(defn resolve-gene-symbol [gene-resolver gene-symbol]
  (with-open [conn (.getConnection (:db-pool gene-resolver))]
    (let [genes (jdbc/execute! conn (create-gene-symbol-query gene-symbol))]
      (if-not (empty? genes)
        (first genes)))))

(defn resolve-entrez-id [gene-resolver entrez-id]
  (with-open [conn (.getConnection (:db-pool gene-resolver))]
    (let [genes (jdbc/execute! conn (create-entrez-id-query entrez-id))]
      (if-not (empty? genes)
        (first genes)))))

