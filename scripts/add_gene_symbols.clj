(ns add-gene-symbols
  (:require [next.jdbc :as jdbc]
            [honeysql.core :as sql]
            [honeysql.helpers :as h]
            [taoensso.timbre :as timbre]
            [failjure.core :as f]
            [script-utils :refer :all])
  (:gen-class))

(defn parse-non-empty-field
  [field parser]
  (if (= "-" field)
    (f/fail "Required field is empty.")
    (parser field)))

(defn parse-gene-info-line
  [line]
  (let [parts (clojure.string/split line #"\t")]
    (if (> (count parts) 3)
      (f/try-all [entrez-id (Integer/parseInt (nth parts 1))
                  gene-symbol (parse-non-empty-field (nth parts 2) identity)
                  gene-synonyms (parse-non-empty-field
                                 (nth parts 4)
                                 #(clojure.string/split % #":"))]
        {:id entrez-id :symbols (into #{gene-symbol} gene-synonyms)}))))

(defn gene-info-line-to-sql-record
  [gene-info]
  (let [f (fn [s] {:entrez_id (:id gene-info) :gene_symbol s})
        records (mapv f (:symbols gene-info))]
    (-> (h/insert-into :gene_symbols)
        (h/values records)
        (sql/format)))) 

(defn add-gene-symbols [conn gene-info-file]
  (doseq [line (lazy-lines-gzip gene-info-file)]
    (f/when-let-ok? [gene-info (parse-gene-info-line line)]
       (->> (gene-info-line-to-sql-record gene-info)
         (jdbc/execute! conn)))))

(defn usage []
  (->> ["Adds gene-symbol information to an existing gene-symbol"
        "database."
        ""
        "Usage: java -jar add-gene-symbols.jar [jdbc-url-string] [gene_info.gz]"]
       (clojure.string/join \newline)))

(defn -main [& args]
  (when (< (count args) 2)
    (println (usage))
    (System/exit 1))
  (let [ds (jdbc/get-datasource {:jdbcUrl (nth args 0)})]
    (with-open [conn (jdbc/get-connection ds)]
      (timbre/info "Successfully retrieved connection to database")
      (timbre/info "Adding genes symbols to gene_symbols table from entrez gene_info.gz file.")
      (add-gene-symbols conn (nth args 1)))))

