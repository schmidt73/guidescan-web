(ns guidescan-web.query.process
  "This namespace exposes an interface for processing gRNA queries that
  applies appropriate filters and sorts them correctly so that they
  are suitable for rendering."
  (:require [guidescan-web.bam.db :as db]
            [guidescan-web.grna :as grna]
            [guidescan-web.query.parsing :refer [parse-query]]
            [failjure.core :as f]))
  
(defn- process-parsed-queries
  "Processes parsed queries, returning a failure object containing the
  first failed query on failure, and the unfiltered and unsorted,
  gRNAs for each [chrX, start, end] triple otherwise."
  [config organism parsed-queries]
  (let [processed-queries
        (map #(apply db/query-bam-grna-db config organism %) parsed-queries)]
     (if (some f/failed? processed-queries)
       (first (filter f/failed? processed-queries))
       processed-queries)))

(defn sort-results
  "Sorts the results of a gRNA query according to the ordering
  specified in the user request map."
  [ordering results [chromosone start-pos end-pos]]
  (case ordering
    "specificity" (sort-by :specificity results)
    "cutting-efficiency" (sort-by :cutting-efficiency results)
    (sort-by grna/num-off-targets results)))

(defn filter-results
  "Filters the results of a gRNA query according to the parameters
  specified in the user request map."
  [req results [chromosone start-pos end-pos]]
  (->> results
       (filter #(and (<= start-pos (:start %))
                     (>= end-pos (:end %))))))

(defn process-query
  "Process the query, returning either a response vector containing the
  processed gRNAs for each [chrX, start, end] input or a failure
  object with an appropriate message."
  [config req]
  (let [parsed-query (parse-query (:params req))
        organism (:organism (:params req))]
    (if-let [query (:success parsed-query)] ; else branch = parse error
      (f/attempt-all
       [results (process-parsed-queries config organism query)]
       (->> results
            (map #(filter-results req %2 %1) query)
            (map #(sort-results (:ordering req) %2 %1) query)))
      (f/fail (:failure parsed-query)))))
