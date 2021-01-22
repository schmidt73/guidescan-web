(ns add-organism
  (:require [next.jdbc :as jdbc]
            [honeysql.core :as sql]
            [honeysql.helpers :as h]
            [honeysql.format :as fmt]
            [taoensso.timbre :as timbre]
            [failjure.core :as f]
            [script-utils :refer :all])
  (:gen-class))

(defn- parse-gtf-field
  [parser field]
  (if (not= field ".") (parser field)))

(defn- parse-gtf-attribute
  [field]
  (let [kv-re #"(?<key>\S*)\s\"(?<value>[^\"]*)\";"
        matcher (re-matcher kv-re field)]
    (loop [attribute (transient {})]
      (if (.find matcher)
        (let [k (.group matcher "key")
              v (.group matcher "value")]
          (if (contains? attribute k)
            (recur (assoc! attribute k (conj (get attribute k) v)))
            (recur (assoc! attribute k [v]))))
        (persistent! attribute)))))

(defn parse-gtf-annotation-line
  "Parses a row out of a GTF file for entry into the gene table."
  [line]
  (let [parts        (clojure.string/split line #"\t")
        chromosome   (parse-gtf-field identity (nth parts 0))
        source       (parse-gtf-field identity (nth parts 1))
        feature-type (parse-gtf-field identity (nth parts 2))
        start        (parse-gtf-field #(Integer/parseInt %) (nth parts 3))
        end          (parse-gtf-field #(Integer/parseInt %) (nth parts 4))
        score        (parse-gtf-field #(Float/parseFloat %) (nth parts 5))
        sense        (parse-gtf-field #(= "+" %) (nth parts 6))
        frame        (parse-gtf-field #(Integer/parseInt %) (nth parts 7))
        attribute    (if (> (count parts) 8) (parse-gtf-attribute (nth parts 8)))]
    (cond-> {}
      chromosome    (assoc :chromosome chromosome)
      source        (assoc :source source)
      feature-type  (assoc :feature_type feature-type)
      start         (assoc :start_pos start)
      end           (assoc :end_pos end)
      score         (assoc :score score)
      (some? sense) (assoc :sense sense)
      frame         (assoc :frame frame)
      attribute     (assoc :attribute attribute))))

(defn parse-entrez-id
  [annotation]
  (if-let [db-xrefs (get-in annotation [:attribute "db_xref"])]
    (if-let [[_ id-str] (some #(re-find #"GeneID:(\d+)" %) db-xrefs)]
      (Integer/parseInt id-str))))

(defn parse-gene-symbols
  [annotation]
  (if-let [gene-symbols (get-in annotation [:attribute "gene"])]
    (concat gene-symbols (get-in annotation [:attribute "gene_synonym"]))))

(defn parse-exon-info
  [annotation]
  (if-let* [product (get-in annotation [:attribute "product" 0])
            exon-number (get-in annotation [:attribute "exon_number" 0])]
    [product (Integer/parseInt exon-number)]))

(defn create-sql-record-exon
  [annotation]
  (if-let* [entrez-id (parse-entrez-id annotation)
            [product exon-number] (parse-exon-info annotation)
            {:keys [chromosome start_pos end_pos sense]} annotation]
    {:entrez_id   entrez-id
     :chromosome  chromosome
     :start_pos   start_pos
     :end_pos     end_pos
     :product     product
     :exon_number exon-number
     :sense       sense}))

(defn create-sql-records-gene
  [annotation]
  (if-let* [entrez-id (parse-entrez-id annotation)
            gene-symbols (parse-gene-symbols annotation)
            {:keys [chromosome start_pos end_pos sense]} annotation]
    (for [gene-symbol gene-symbols]
      {:entrez_id   entrez-id
       :chromosome  chromosome
       :start_pos   start_pos
       :end_pos     end_pos
       :gene_symbol gene-symbol
       :sense       sense})))

(defmethod fmt/format-clause :on-conflict-nothing [[op v] sqlmap]
  (str "ON CONFLICT DO NOTHING"))

(h/defhelper on-conflict-nothing [m args]
  (assoc m :on-conflict-nothing nil))

(defmulti create-sql-statement
  "Returns a SQL statement to update the database
  with the given annotation or nil on failure to parse."
  (fn [annotation] (:feature_type annotation)))

(defmethod create-sql-statement "gene"
  [annotation]
  (if-let [records (create-sql-records-gene annotation)]
    (-> (h/insert-into :genes)
        (h/values records)
        (on-conflict-nothing)
        sql/format)))

(defmethod create-sql-statement "exon"
  [annotation]
  (if-let [record (create-sql-record-exon annotation)]
    (-> (h/insert-into :exons)
        (h/values [record])
        (on-conflict-nothing)
        sql/format)))

(defmethod create-sql-statement :default
  [_]
  nil)

(defn add-organism-to-gene-table
  [db-conn organism-gtf-file]
  (doseq [line (lazy-lines-gzip organism-gtf-file)]
    (f/attempt-all [annotation (f/try* (parse-gtf-annotation-line line))]
       (if-let [stmt (create-sql-statement annotation)]
         (jdbc/execute! db-conn stmt)))))

(defn add-organism-to-chromosome-table
  [db-conn chr2acc-file organism]
  (with-open [rdr (clojure.java.io/reader chr2acc-file)]
    (doseq [line (rest (line-seq rdr))]
      (let [[name accession] (clojure.string/split line #"\s")
            record {:name name :accession accession :organism organism}]
        (jdbc/execute! db-conn
                       (-> (h/insert-into :chromosomes)
                           (h/values [record])
                           (on-conflict-nothing)
                           sql/format))))))

(defn usage []
  (->> ["Adds an organism to an existing gene database."
        ""
        "Usage: java -jar add-organism.jar [jdbc-url-string] [organism.gtf.gz] [organism_chr2acc] [organism-name]"]
       (clojure.string/join \newline)))

(defn -main [& args]
  (when (< (count args) 2)
    (println (usage))
    (System/exit 1))
  (let [ds (jdbc/get-datasource {:jdbcUrl (nth args 0)})]
    (with-open [conn (jdbc/get-connection ds)]
      (timbre/info "Successfully retrieved connection to database")
      (timbre/info "Adding annotations to genes and exons tables from organism's GTF file.")
      (add-organism-to-gene-table conn (nth args 1))
      (add-organism-to-chromosome-table conn (nth args 2) (nth args 3)))))

