(ns guidescan-web.genomics.resolver
  "Defines a component that resolves various types of genomic names."
  (:import (com.mchange.v2.c3p0 ComboPooledDataSource))
  (:require [guidescan-web.utils :refer :all]
            [taoensso.timbre :as timbre]
            [next.jdbc :as jdbc]
            [cheshire.core :as cheshire]
            [clj-http.client :as http]
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

(defn- create-entrez-id-query [organism entrez-id]
  (sql/format
   (sql/build
    :select [:genes/entrez_id :genes/gene_symbol :genes/start_pos
             :genes/end_pos :genes/sense :chromosomes/name]
    :from [:genes :chromosomes]
    :where [:and
            [:= :genes/entrez_id entrez-id]
            [:= :genes/chromosome :chromosomes/accession]
            [:= organism :chromosomes/organism]]))) 

(defn- create-gene-symbol-query [organism gene-symbol]
  (sql/format
   (sql/build
    :select [:genes/entrez_id :genes/gene_symbol :genes/start_pos
             :genes/end_pos :genes/sense :chromosomes/name]
    :from [:genes :chromosomes]
    :where [:and
            [:= :genes/gene_symbol gene-symbol]
            [:= :genes/chromosome :chromosomes/accession]
            [:= organism :chromosomes/organism]]))) 

(defn- create-chromosome-accession-query [organism accession]
  (sql/format
   (sql/build
    :select :*
    :from [:chromosomes]
    :where [:and
            [:= accession :chromosomes/accession]
            [:= organism :chromosomes/organism]]))) 

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

(defn resolve-gene-symbol [gene-resolver organism gene-symbol]
  (try
    (with-open [conn (.getConnection (:db-pool gene-resolver))]
      (let [genes (jdbc/execute! conn (create-gene-symbol-query organism gene-symbol))]
        (if-not (empty? genes)
          (first genes))))
    (catch java.sql.SQLException e
      (timbre/error "Cannot acquire gene-resolver DB connection.")
      nil)))

(defn resolve-entrez-id [gene-resolver organism entrez-id]
  (try
    (with-open [conn (.getConnection (:db-pool gene-resolver))]
      (let [genes (jdbc/execute! conn (create-entrez-id-query organism entrez-id))]
        (if-not (empty? genes)
          (first genes))))
    (catch java.sql.SQLException e
      (timbre/error "Cannot acquire gene-resolver DB connection.")
      nil)))

(defn resolve-chromosome-accession [gene-resolver organism accession]
  (try
    (with-open [conn (.getConnection (:db-pool gene-resolver))]
      (let [genes (jdbc/execute! conn (create-chromosome-accession-query organism accession))]
        (if-not (empty? genes)
          (first genes))))
    (catch java.sql.SQLException e
      (timbre/error "Cannot acquire gene-resolver DB connection.")
      nil)))

(def ^:private random-dna-seq
  (clojure.string/join (map (fn [_] (rand-nth "ATCG")) (range 500)))) 

(defn- resolve-sequence-raw
  [url sequence]
  (try
    (let [endpoint (format "%s/search" url)
          result (http/get endpoint {:accept :json :query-params {"sequence" sequence}})]
      (if (= 200 (:status result))
        (cheshire/decode (:body result))))
    (catch java.net.ConnectException e
        (timbre/error (format "Could not access :resolve-sequence url %s"
                              url)))))

(defrecord SequenceResolver [sequence-resolvers gene-resolver config]
  component/Lifecycle
  (start [this]
    (if-let [conns (get-in config [:config :sequence-resolvers])]
      (do
        (timbre/info "Checking sequence-resolvers")
        (for [organism (keys conns)]
          (if-not (resolve-sequence-raw (get-in conns [organism :url])
                                        random-dna-seq)
            (timbre/warn
             (format
              "organism %s's search endpoint does not appear to be available"
              organism))))
        (timbre/info "Successfully checked sequence-resolvers")
        (assoc this :sequence-resolvers conns))
      (do
        (timbre/warn (str ":sequence-resolvers key not found in config, " 
                          "search by sequence disabled."))
        this)))

  (stop [this]
    (assoc this :sequence-resolvers nil)))

(defn sequence-resolver []
  (map->SequenceResolver {}))

(defn resolve-sequence
  [resolver organism sequence]
  (if-let* [gene-resolver (:gene-resolver resolver)
            url (get-in resolver [:sequence-resolvers organism :url])
            result (resolve-sequence-raw url sequence)
            {accession "chr" d "distance" p "pos" s "strand"} (first (sort-by #(get % "distance") result))
            {chr :chromosomes/name} (resolve-chromosome-accession gene-resolver organism accession)]
    {:chr chr :distance d :pos p :strand s}))
