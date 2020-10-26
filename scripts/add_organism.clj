(ns add-organism
  (:require [next.jdbc :as jdbc]
            [honeysql.core :as sql]
            [honeysql.helpers :as h]
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
        matcher (re-matcher kv-re field)
        attribute (transient {})]
    (while (.find matcher)
      (assoc! attribute (.group matcher "key") (.group matcher "value")))
    (persistent! attribute)))

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
  (if-let [db-xref (get-in annotation [:attribute "db_xref"])]
    (if-let [[_ id-str] (re-find #"GeneID:(\d+)" db-xref)]
      (Integer/parseInt id-str))))

(defn parse-gene-symbol
  [annotation]
  (if-let [gene-symbol (get-in annotation [:attribute "gene"])]
    gene-symbol))

(defn annotation-to-sql-record
  [annotation]
  (if-let [entrez-id (parse-entrez-id annotation)]
    (if-let [gene-symbol (parse-gene-symbol annotation)]
      (if (and (= (:feature_type annotation) "gene")
               (every? #(contains? annotation %)
                       [:chromosome :start_pos :end_pos :sense]))
        {:entrez_id   entrez-id
         :chromosome  (:chromosome annotation)
         :start_pos   (:start_pos annotation)
         :end_pos     (:end_pos annotation)
         :gene_symbol gene-symbol
         :sense       (:sense annotation)}))))

(defn add-organism-to-gene-table
  [db-conn organism-gtf-file]
  (doseq [line (lazy-lines-gzip organism-gtf-file)]
    (f/attempt-all [annotation (f/try* (parse-gtf-annotation-line line))]
       (if-let [record (annotation-to-sql-record annotation)]
         (jdbc/execute! db-conn
                        (-> (h/insert-into :genes)
                            (h/values [record])
                            sql/format))))))

(defn usage []
  (->> ["Adds an organism to an existing gene database."
        ""
        "Usage: java -jar add-organism.jar [jdbc-url-string] [organism.gtf.gz]"]
       (clojure.string/join \newline)))

(defn -main [& args]
  (when (< (count args) 2)
    (println (usage))
    (System/exit 1))
  (let [ds (jdbc/get-datasource {:jdbcUrl (nth args 0)})]
    (with-open [conn (jdbc/get-connection ds)]
      (timbre/info "Successfully retrieved connection to database")
      (timbre/info "Adding genes to genes table from organism's GTF file.")
      (add-organism-to-gene-table conn (nth args 1)))))

