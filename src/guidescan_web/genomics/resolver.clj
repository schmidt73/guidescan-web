(ns guidescan-web.genomics.resolver
  "Defines a component that resolves various types of genomic names, for
  example protein names such as TP53 or sequences found in the
  organism of interest."
  (:require [guidescan-web.utils :refer :all]
            [taoensso.timbre :as timbre]
            [next.jdbc :as jdbc]
            [cheshire.core :as cheshire]
            [clj-http.client :as http]
            [honeysql.core :as sql]
            [guidescan-web.db-pool :as db-pool]
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
             :genes/end_pos :genes/sense :chromosomes/name :chromosomes/accession]
    :from [:genes :chromosomes]
    :where [:and
            [:= :genes/gene_symbol gene-symbol]
            [:= :genes/chromosome :chromosomes/accession]
            [:= organism :chromosomes/organism]]))) 

(defn- create-gene-symbol-suggestions-query [organism gene-symbol-prefix]
  (sql/format
   (sql/build
    :select [:genes/gene_symbol]
    :from [:genes :chromosomes]
    :where [:and
            [:like :genes/gene_symbol (str gene-symbol-prefix "%")]
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

(defn- create-chromosome-name-query [organism chr]
  (sql/format
   (sql/build
    :select :*
    :from [:chromosomes]
    :where [:and
            [:= chr :chromosomes/name]
            [:= organism :chromosomes/organism]]))) 

(defn create-chromosome-map [gene-resolver]
  (try
    (with-open [conn (db-pool/get-db-conn (:db-pool gene-resolver))]
      (let [chrms (jdbc/execute! conn (sql/format (sql/build :select :* :from [:chromosomes])))]
        (->> chrms
         (group-by (fn [m] {:organism (:chromosomes/organism m) :name (:chromosomes/name m)}))
         (map #(vector (first %) (:chromosomes/accession (first (second %)))))
         (into {}))))
    (catch java.sql.SQLException e
      (timbre/error "Cannot acquire gene-resolver DB connection.")
      nil)))

(defrecord GeneResolver [db-pool config chrm-map accession-map]
  component/Lifecycle
  (start [this]
    (let [chrm-map (create-chromosome-map this)
          accession-map (clojure.set/map-invert chrm-map)]
      (assoc this :chrm-map chrm-map :accession-map accession-map)))
  (stop [this] this))

(defn gene-resolver []
  (map->GeneResolver {}))

(defn resolve-gene-symbol [gene-resolver organism gene-symbol]
  (try
    (with-open [conn (db-pool/get-db-conn (:db-pool gene-resolver))]
      (let [genes (jdbc/execute! conn (create-gene-symbol-query organism gene-symbol))]
        (if-not (empty? genes)
          (first genes))))
    (catch java.sql.SQLException e
      (timbre/error "Cannot acquire gene-resolver DB connection.")
      nil)))

(defn resolve-entrez-id [gene-resolver organism entrez-id]
  (try
    (with-open [conn (db-pool/get-db-conn (:db-pool gene-resolver))]
      (let [genes (jdbc/execute! conn (create-entrez-id-query organism entrez-id))]
        (if-not (empty? genes)
          (first genes))))
    (catch java.sql.SQLException e
      (timbre/error "Cannot acquire gene-resolver DB connection.")
      nil)))

(defn resolve-chromosome-accession [gene-resolver organism accession]
  (:name (get (:accession-map gene-resolver) accession)))

(defn resolve-chromosome-name [gene-resolver organism chr-name]
  (get (:chrm-map gene-resolver) {:organism organism :name chr-name}))

(defn resolve-gene-symbol-suggestion [gene-resolver organism gene-symbol-suggestion]
  (try
    (with-open [conn (db-pool/get-db-conn (:db-pool gene-resolver))]
      (let [genes (jdbc/execute! conn (create-gene-symbol-suggestions-query
                                       organism
                                       gene-symbol-suggestion))]
        (map :genes/gene_symbol genes)))
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
            result (resolve-sequence-raw url sequence)]
    (if (> (count result) 1)
      (f/fail "Multiple perfect matches found. Cannot resolve sequence to unique coordinates.")
      (let [{accession "chr" d "distance" p "pos" s "strand"} (first (sort-by #(get % "distance") result))
            chr (resolve-chromosome-accession gene-resolver organism accession)]
        {:chr chr :distance d :pos p :strand s}))
    (f/fail "No match found for input sequence.")))

(defn resolve-sequence-in-gene
  [resolver organism gene-symbol sequence]
  (if-let* [gene-resolver (:gene-resolver resolver)
            {:genes/keys [start_pos end_pos] accession :chromosomes/accession chr :chromosomes/name}
            (resolve-gene-symbol gene-resolver organism gene-symbol)
            in-gene? (fn [{accession "chr" pos "pos"}]
                       (and (>= pos start_pos)
                            (<= pos end_pos)
                            (= accession accession)))
            url (get-in resolver [:sequence-resolvers organism :url])
            result (resolve-sequence-raw url sequence)
            result-in-gene (filter in-gene? result)]
    (if (> (count result-in-gene) 1)
      (f/fail "Multiple perfect matches found within gene. Cannot resolve sequence to unique coordinates.")
      (if (= (count result-in-gene) 0)
        (f/fail "No match found for input sequence within gene \"%s\"." gene-symbol)
        (let [{d "distance" p "pos" s "strand"} (first result-in-gene)]
          {:chr chr :distance d :pos p :strand s})))
    (f/fail "Gene symbol \"%s\" not found." gene-symbol)))
