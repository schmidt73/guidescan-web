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
        "                        gene_symbol VARCHAR(1023) NOT NULL,"
        "                        identifier VARCHAR(1023) NOT NULL,"
        "                        ots_summary VARCHAR(1023),"
        "                        native_score REAL,"
        "                        cfd_score REAL,"
        "                        ce_score REAL,"
        "                        PRIMARY KEY (gene_symbol, identifier));"]
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

(defn create-sql-record-guides
  [organism csv-entry]
  (let [native-score (get csv-entry "score")]
    {:grna (get csv-entry "sgRNA")
     :identifier (get csv-entry "identifier")
     :native_score (if (= native-score "NA")
                     nil
                     (Float/parseFloat native-score))
     :organism organism
     :source (get csv-entry "source")
     :cfd_score (Float/parseFloat (get csv-entry "cfd_score"))
     :gene_symbol (get csv-entry "gene")
     :ots_summary (get csv-entry "ots")}))

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
        "Usage: java -jar add-grna-lib.jar [jdbc-url-string] [library-csv] [essential-genes] [organism]"]
       (clojure.string/join \newline)))

(defn- main
  [& args]
  (when (< (count args) 2)
    (println (usage))
    (System/exit 1))
  (let [ds (jdbc/get-datasource {:jdbcUrl (nth args 0)})
        library-csv (read-csv-with-header (nth args 1))
        essential-genes-list (clojure.string/split-lines (slurp (nth args 2)))
        organism (nth args 3)
        entries (partition 500 library-csv)]
    (with-open [conn (jdbc/get-connection ds)]
      (jdbc/execute! conn [create-grna-library-table])
      (jdbc/execute! conn [create-essential-genes-table])
      (doseq [entry entries]
        (jdbc/execute! conn (create-sql-statement-guides organism entry)))
      (doseq [essential-genes (partition 500 essential-genes-list)]
        (jdbc/execute! conn (create-sql-statement-genes organism essential-genes))))))
