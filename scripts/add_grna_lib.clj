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
        "                        identifier VARCHAR(1023),"
        "                        region_id VARCHAR(1023),"
        "                        offtarget0 INT NOT NULL,"
        "                        offtarget1 INT NOT NULL,"
        "                        offtarget2 INT NOT NULL,"
        "                        offtarget3 INT NOT NULL,"
        "                        specificity REAL,"
        "                        specificity_5pG REAL,"
        "                        cutting_efficiency REAL,"
        "                        PRIMARY KEY (gene_symbol, grna, grna_type));"]
       (clojure.string/join "\n")))

(defmethod fmt/format-clause :on-conflict-nothing [[op v] sqlmap]
  (str "ON CONFLICT DO NOTHING"))

(h/defhelper on-conflict-nothing [m args]
  (assoc m :on-conflict-nothing nil))

(defn nil-parse-float [s] (if-not (empty? s) (Float/parseFloat s)))

(defn create-sql-record-guides
  [organism csv-entry]
  {:grna (get csv-entry "sgRNA")
   :identifier (get csv-entry "Identifier")
   :organism organism
   :source "Guidescan2"
   :region_id (get csv-entry "Cutting Region ID")
   :specificity (nil-parse-float (get csv-entry "Specificity"))
   :specificity_5pG (nil-parse-float (get csv-entry "5pG Specificity"))
   :cutting_efficiency (nil-parse-float (get csv-entry "Cutting Efficiency"))
   :gene_symbol (get csv-entry "Gene")
   :grna_type (get csv-entry "Type")
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

(defn usage []
  (->> ["Adds a new guide-rna library to the database."
        ""
        "Usage: java -jar add-grna-lib.jar [jdbc-url-string] [library-csv] [organism]"]
       (clojure.string/join \newline)))

(defn -main
  [& args]
  (when (< (count args) 2)
    (println (usage))
    (System/exit 1))
  (let [ds (jdbc/get-datasource {:jdbcUrl (nth args 0)})
        library-csv (read-csv-with-header (nth args 1))
        organism (nth args 2)
        entries (partition 500 library-csv)]
    (with-open [conn (jdbc/get-connection ds)]
      (jdbc/execute! conn [create-grna-library-table])
      (doseq [entry entries]
        (jdbc/execute! conn (create-sql-statement-guides organism entry))))))
