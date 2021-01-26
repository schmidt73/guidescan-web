(ns guidescan-web.query.library-design
  (:require [guidescan-web.utils :refer :all]
            [taoensso.timbre :as timbre]
            [next.jdbc :as jdbc]
            [cheshire.core :as cheshire]
            [honeysql.core :as sql]
            [guidescan-web.db-pool :as db-pool]
            [honeysql.helpers :as h]
            [failjure.core :as f]
            [com.stuartsierra.component :as component]))

(defn- find-gene-synonyms-query
  "Finds all the synonyms for a given gene symbol."
  [gene-sym]
  (sql/build
   :select :genes/gene_symbol
   :from :genes
   :where [:in
           :genes/entrez_id
           (sql/build
            :select :genes/entrez_id
            :from :genes
            :where [:= :genes/gene_symbol gene-sym])]))

(defn- find-precomputed-guides-query
  [gene-sym organism]
  (sql/format
   (sql/build
    :select :*
    :from [:libraries]
    :where [:and
            [:= organism :libraries/organism]
            [:in :libraries/gene_symbol (find-gene-synonyms-query gene-sym)]])))
 
(defn- find-precomputed-controls-query
  [organism n]
  (sql/format
   (sql/build
    :select :*
    :from [:libraries]
    :order-by [:%random]
    :where [:and
            [:= :libraries/gene_symbol "safe"]
            [:= :libraries/organism organism]]
    :limit n)))

(defn- find-precomputed-essential-genes-query
  [organism n]
  (sql/format
   (sql/build
    :select :*
    :from [:essential_genes]
    :order-by [:%random]
    :where [:= :essential_genes/organism organism]
    :limit n)))

(defn- find-precomputed-guides
  "Finds all the precomputed gRNAs for a given gene (specified by gene
  symbol) and organism."
  [db-pool organism gene]
  (with-open [conn (db-pool/get-db-conn db-pool)]
    (jdbc/execute! conn (find-precomputed-guides-query gene organism))))

(defn- find-precomputed-controls
  "Randomly finds n precomputed control gRNAs."
  [db-pool organism n]
  (with-open [conn (db-pool/get-db-conn db-pool)]
    (jdbc/execute! conn (find-precomputed-controls-query organism n))))

(defn- find-precomputed-essentials
  [db-pool organism num-essentials]
  (with-open [conn (db-pool/get-db-conn db-pool)]
    (let [genes (jdbc/execute!
                 conn
                 (find-precomputed-essential-genes-query organism num-essentials))]
      (doall
        (for [gene genes]
          (let [gene-sym (:essential_genes/gene_symbol gene)
                guides
                (->> (find-precomputed-guides-query gene-sym organism)
                     (jdbc/execute! conn))]
            {:essential-gene gene
             :guides guides}))))))

(def ^:private adapters
  {:prime5 "CGTCTCACACC"
   :prime3 "GTTTCGAGACG"})

(def ^:private barcodes
  (for 
   [left  ["AGGCACTTGCTCGTACGACG"
           "GTGTAACCCGTAGGGCACCT"    
           "CAGCGCCAATGGGCTTTCGA"    
           "CTACAGGTACCGGTCCTGAG"    
           "CATGTTGCCCTGAGGCACAG"    
           "GGTCGTCGCATCACAATGCG"]
    right ["TTAAGGTGCCGGGCCCACAT"
           "GTCGAAGGACTGCTCTCGAC"
           "CGACAGGCTCTTAAGCGGCT"
           "CGGATCGTCACGCTAGGTAC"
           "AGCCTTTCGGGACCTAACGG"
           "CGTCACATTGGCGCTCGAGA"]]
   [left right]))

(defn- find-guides-for-gene
  [db-pool organism gene]
  (let [guides (find-precomputed-guides db-pool organism gene)]
    (if (empty? guides)
      (f/fail (format "Gene: \"%s\" not found for organism %s" gene organism))
      {:gene gene
       :guides guides})))

(defn- find-guides-for-query
  [db-pool query-text organism]
  (->> (clojure.string/split-lines query-text)
       (map clojure.string/trim)
       (map #(find-guides-for-gene db-pool organism %))
       (doall)))

(defn- insert-adapters
  [library pool-num {:keys [prime5-g]}]
  (let [create-oligo #(let [[l r] (nth barcodes pool-num)]
                        (if prime5-g
                          (str l (:prime5 adapters) "G" (subs 1 %) (:prime3 adapters) r)
                          (str l (:prime5 adapters) % (:prime3 adapters) r)))
        update-guide (fn [guide]
                       (assoc guide :library_oligo (create-oligo (:libraries/grna guide))))]
    (map (fn [entry] (update entry :guides #(map update-guide %)))
         library)))

(defn design-pool
  "Designs a pool around a set of genes given some parameters."
  [db-pool organism pool {:keys [saturation num-essential num-control]}]
  (let [essential (find-precomputed-essentials db-pool organism num-essential)
        controls (find-precomputed-controls db-pool organism num-control)
        pick-fn
        (fn [guides]
          (->> guides
              (sort-by :libraries/native_score)
              (sort-by :libraries/cfd_score)
              (distinct-by :libraries/grna)
              (reverse)
              (take saturation)))]
    (concat (map #(update % :guides pick-fn) pool)
            (map #(update % :guides pick-fn) essential)
            [{:type :controls :guides controls}])))

(defn design-library
  "Designs a saturation mutagenesis library using the pre-computed
  gRNA libraries found in the database."
  [db-pool query-text organism
   {:keys [num-pools saturation num-essential num-control]}]
  (let [num-pools (or num-pools 1)
        saturation (or saturation 6)
        num-essential (or num-essential 0)
        num-control (or num-control 0)
        results (find-guides-for-query db-pool query-text organism)
        failed-genes (filter f/failed? results)
        successful-genes (filter f/ok? results)
        partition-size (/ (count successful-genes) num-pools)
        pools (partition-all partition-size successful-genes)]
    (for [i (range (count pools))]
      (-> (design-pool db-pool organism (nth pools i)
                       {:saturation saturation
                        :num-essential num-essential
                        :num-control num-control})
          (insert-adapters i {})))))
