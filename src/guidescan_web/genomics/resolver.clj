(ns guidescan-web.genomics.resolver
  "Defines a component that resolves various types of genomic names, for
  example protein names such as TP53 or sequences found in the
  organism of interest."
  (:import (com.mchange.v2.c3p0 ComboPooledDataSource)
           (guidescan.algo RabinKarp))
  (:require [taoensso.timbre :as timbre]
            [next.jdbc :as jdbc]
            [honeysql.core :as sql]
            [guidescan-web.genomics.structure :as genome-structure]
            [honeysql.helpers :as h]
            [failjure.core :as f]
            [com.stuartsierra.component :as component]))

(defn- load-genome-structure-raw
  "Parses the genome out of a FASTA file into a vector of
  [chromosome-name chromosome-length]."
  [fasta-file]
  (with-open [rdr (clojure.java.io/reader fasta-file)]
    (->> (line-seq rdr)
         (reduce (fn [structure row]
                   (if (= (nth row 0) \>)
                     (cons [(subs (first (clojure.string/split row #" ")) 1) 0]
                           structure)
                     (cons (update (first structure) 1 #(+ (count row) %))
                           (rest structure))))
                 '())
         (reverse))))

(defn- load-genome-sequence
  [fasta-file]
  (with-open [rdr (clojure.java.io/reader fasta-file)]
    (->> (line-seq rdr)
         (mapcat #(if (= \> (first %)) "" (clojure.string/upper-case %)))
         (clojure.string/join))))

(defn- load-genome
  [organism-fasta]
  {:sequence (load-genome-sequence organism-fasta) 
   :structure (-> (load-genome-structure-raw organism-fasta)
                  (genome-structure/get-genome-structure))})

(defn load-genome-structures
  [fasta-map]
  (->> (map #(vector (first %) (load-genome (second %))) fasta-map)
       (into {})))

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

(defn- create-chromosome-name-query [organism accession]
  (sql/format
   (sql/build
    :select [:chromosomes/name]
    :from [:chromosomes]
    :where [:and
            [:= accession :chromosomes/accession]
            [:= organism  :chromosomes/organism]]))) 

(defrecord GeneResolver [db-pool genome-structures config]
  component/Lifecycle
  (start [this]
    (let [gr-map (atom this)]
      (if-let [db-spec (:db-spec (:config config))]
        (let [pool (create-pool db-spec)]
          (timbre/info "Successfully initialized DB connection pool for gene name resolution.")
          (swap! gr-map #(assoc % :db-pool pool)))
        (timbre/warn "DB not specified, gene name resolution will not be supported."))
      (if-let [organisms (:organism-sequences (:config config))]
        (if-not (empty? organisms)
          (let [genome-structures (load-genome-structures organisms)]
            (timbre/info "Successfully loaded genome-structures for search by sequence.")
            (swap! gr-map #(assoc % :genome-structures genome-structures)))
          (timbre/warn (str "Organism sequences map empty, search by"
                             " sequence will not be supported.")))
        (timbre/warn (str "Organism sequences map not found, search by"
                          " sequence will not be supported.")))
     @gr-map))
  (stop [this]
    (.close db-pool)
    (assoc this :db-pool nil)))

(defn gene-resolver []
  (map->GeneResolver {}))

(defn resolve-gene-symbol [gene-resolver organism gene-symbol]
  (with-open [conn (.getConnection (:db-pool gene-resolver))]
    (let [genes (jdbc/execute! conn (create-gene-symbol-query organism gene-symbol))]
      (if-not (empty? genes)
        (first genes)))))

(defn resolve-entrez-id [gene-resolver organism entrez-id]
  (with-open [conn (.getConnection (:db-pool gene-resolver))]
    (let [genes (jdbc/execute! conn (create-entrez-id-query organism entrez-id))]
      (if-not (empty? genes)
        (first genes)))))

(defn resolve-chromosome-accession
  [gene-resolver organism accession]
  (with-open [conn (.getConnection (:db-pool gene-resolver))]
    (let [chrs (jdbc/execute! conn (create-chromosome-name-query organism accession))]
      (if-not (empty? chrs)
        (first chrs)))))

(defn- reverse-complement
  [seq]
  (->> seq
       (map #(case %
               \A \T
               \T \A
               \G \C
               \C \G
               %))
       (reverse)
       (clojure.string/join)))

(defn- search-genome
  [{:keys [structure sequence]} search-sequence]
  (let [forward-pos (-> (RabinKarp. search-sequence)
                        (.search sequence))]
    (if-not (= forward-pos (count sequence))
      (genome-structure/to-genomic-coordinates structure forward-pos)
      (let [backward-pos (-> (RabinKarp. (reverse-complement search-sequence))
                             (.search sequence))]
        (if-not (= backward-pos (count sequence))
          (genome-structure/to-genomic-coordinates structure (- backward-pos)))))))

(defn resolve-sequence [gene-resolver organism sequence]
  (if-let [gs (get (:genome-structures gene-resolver) organism)]
    (if-let [coords (search-genome gs sequence)]
      (if-let [chr (resolve-chromosome-accession gene-resolver organism (:chromosome coords))]
        (assoc coords :chromosome (:chromosomes/name chr))))))


