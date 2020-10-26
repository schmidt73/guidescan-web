(ns create-gene-db
  (:require [next.jdbc :as jdbc]
            [taoensso.timbre :as timbre])
  (:gen-class))

(def drop-tables
  "DROP TABLE IF EXISTS genes;")

(def create-gene-id-table
  (->> ["CREATE TABLE genes (entrez_id INT NOT NULL,"
        "                    gene_symbol VARCHAR(1023) NOT NULL,"
        "                    chromosome VARCHAR(1023) NOT NULL,"
        "                    sense BOOL NOT NULL,"
        "                    start_pos INT NOT NULL,"
        "                    end_pos INT NOT NULL,"
        "                    PRIMARY KEY (gene_symbol, entrez_id));"]
       (clojure.string/join "\n")))

(defn create-db [jdbc-url]
  (with-open [conn (jdbc/get-connection (jdbc/get-datasource {:jdbcUrl jdbc-url}))]
    (timbre/info "Successfully retrieved connection to database")
    (timbre/info "Dropping old tables.")
    (jdbc/execute! conn [drop-tables])
    (jdbc/execute! conn [create-gene-id-table])
    (timbre/info "Created genes table.")))

(defn usage []
  (->> ["Creates an empty gene database, deleting old tables if they exist."
        ""
        "Usage: java -jar create-gene-db.jar [jdbc-url-string]"]
       (clojure.string/join \newline)))

(defn -main [& args]
  (when (< (count args) 1)
    (println (usage))
    (System/exit 1))
  (create-db (nth args 0)))
