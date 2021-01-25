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

(defmethod fmt/format-clause :on-conflict-nothing [[op v] sqlmap]
  (str "ON CONFLICT DO NOTHING"))

(h/defhelper on-conflict-nothing [m args]
  (assoc m :on-conflict-nothing nil))

(defn create-sql-record
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

(defn create-sql-statement
  [organism csv-entries]
  (let [records (map #(create-sql-record organism %) csv-entries)]
    (-> (h/insert-into :libraries)
        (h/values records)
        (on-conflict-nothing)
        sql/format)))

(defn usage []
  (->> ["Creates an empty gene database, deleting old tables if they exist."
        ""
        "Usage: java -jar add-grna-lib.jar [jdbc-url-string] [library-csv] [organism]"]
       (clojure.string/join \newline)))

(defn- main
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
        (jdbc/execute! conn (create-sql-statement organism entry))))))
