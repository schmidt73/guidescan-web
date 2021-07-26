(ns add-grna-lib
  (:require [next.jdbc :as jdbc]
            [honeysql.core :as sql]
            [honeysql.helpers :as h]
            [honeysql.format :as fmt]
            [taoensso.timbre :as timbre]
            [clojure.data.csv :as csv]
            [failjure.core :as f]
            [script-utils :refer :all])
  (:gen-class))

(def create-grna-library-table
  (->> ["CREATE TABLE IF NOT EXISTS"
        "             libraries (grna VARCHAR(1023) NOT NULL,"
        "                        organism VARCHAR(1023) NOT NULL,"
        "                        source VARCHAR(1023) NOT NULL,"
        "                        gene_symbol VARCHAR(1023),"
        "                        grna_type VARCHAR(1023) NOT NULL,"
        "                        chromosome VARCHAR(1023),"
        "                        identifier VARCHAR(1023),"
        "                        region_id VARCHAR(1023),"
        "                        strand VARCHAR(1023),"
        "                        position INT,"
        "                        offtarget0 INT NOT NULL,"
        "                        offtarget1 INT NOT NULL,"
        "                        offtarget2 INT NOT NULL,"
        "                        offtarget3 INT NOT NULL,"
        "                        specificity REAL,"
        "                        specificity_5pG REAL,"
        "                        cutting_efficiency REAL,"
        "                        PRIMARY KEY (grna, grna_type));"]
       (clojure.string/join "\n")))

(def create-essential-genes-table
  (->> ["CREATE TABLE IF NOT EXISTS"
        "       essential_genes (gene_symbol VARCHAR(1023) NOT NULL,"
        "                        organism VARCHAR(1023) NOT NULL,"
        "                        PRIMARY KEY (gene_symbol, organism));"]
       (clojure.string/join "\n")))

(defmethod fmt/format-clause :on-conflict-nothing [[op v] sqlmap]
  (str "ON CONFLICT DO NOTHING"))

(h/defhelper on-conflict-nothing [m args]
  (assoc m :on-conflict-nothing nil))

(defn nil-parse-int [s]
  (if-not (empty? s)
    (Math/round (Float/parseFloat s))))

(defn nil-parse-float [s]
  (if-not (empty? s)
    (Float/parseFloat s)))

(defn nil-get-entry [csv-entry k]
  (let [v (get csv-entry k)]
    (if-not (empty? v)
      v)))

(defn create-sql-record-guides
  [organism csv-entry]
  {:grna (nil-get-entry csv-entry "sgRNA")
   :identifier (nil-get-entry csv-entry "Identifier")
   :organism organism
   :source "Guidescan2"
   :region_id (nil-get-entry csv-entry "Cutting Region ID")
   :specificity (nil-parse-float (get csv-entry "Specificity"))
   :specificity_5pG (nil-parse-float (get csv-entry "5pG Specificity"))
   :cutting_efficiency (nil-parse-float (get csv-entry "Cutting Efficiency"))
   :gene_symbol (nil-get-entry csv-entry "Gene")
   :grna_type (nil-get-entry csv-entry "Type")
   :strand (nil-get-entry csv-entry "Strand")
   :chromosome (nil-get-entry csv-entry "Chr")
   :position (nil-parse-int (get csv-entry "Pos"))
   :offtarget0 (Integer/parseInt (get csv-entry "0 Off-Targets"))
   :offtarget1 (Integer/parseInt (get csv-entry "1 Off-Targets"))
   :offtarget2 (Integer/parseInt (get csv-entry "2 Off-Targets"))
   :offtarget3 (Integer/parseInt (get csv-entry "3 Off-Targets"))})

(defn create-sql-statement-guides
  [organism csv-entries]
  (let [records (map #(create-sql-record-guides organism %) csv-entries)]
    (-> (h/insert-into :libraries)
        (h/values records)
        (on-conflict-nothing)
        sql/format)))

(defn create-sql-statement-genes
  [organism essential-genes]
  (-> (h/insert-into :essential_genes)
      (h/values (map (fn [gene-sym] {:gene_symbol gene-sym :organism organism})
                     essential-genes))
      (on-conflict-nothing)
      sql/format))

(defn usage []
  (->> ["Adds a new guide-rna library to the database."
        ""
        "Usage: java -jar add-grna-lib.jar [jdbc-url-string] [library-csv] [essential-gene-list] [organism]"]
       (clojure.string/join \newline)))

(defn -main
  [& args]
  (when (< (count args) 3)
    (println (usage))
    (System/exit 1))
  (let [ds (jdbc/get-datasource {:jdbcUrl (nth args 0)})
        library-csv (read-csv-with-header (nth args 1))
        essential-genes-list (clojure.string/split-lines (slurp (nth args 2)))
        organism (nth args 3)
        entries (partition-all 500 library-csv)]
    (with-open [conn (jdbc/get-connection ds)]
      (jdbc/execute! conn [create-grna-library-table])
      (jdbc/execute! conn [create-essential-genes-table])
      (doseq [essential-genes (partition-all 500 essential-genes-list)]
        (jdbc/execute! conn (create-sql-statement-genes organism essential-genes)))
      (doseq [entry entries]
        (jdbc/execute! conn (create-sql-statement-guides organism entry))))))
