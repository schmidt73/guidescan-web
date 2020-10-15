(ns guidescan-web.query.process
  "This namespace exposes an interface for processing gRNA queries that
  applies appropriate filters and sorts them correctly so that they
  are suitable for rendering."
  (:require [guidescan-web.bam.db :as db]
            [guidescan-web.genomics.grna :as grna]
            [guidescan-web.genomics.annotations :as annotations]
            [guidescan-web.query.parsing :refer [parse-query]]
            [failjure.core :as f]))
  
(defn- process-parsed-queries
  "Processes parsed queries, returning a failure object containing the
  first failed query on failure, and the unfiltered and unsorted,
  gRNAs for each [chrX, start, end] triple otherwise."
  [bam-db organism enzyme parsed-queries]
  (let [processed-queries
        (map #(apply db/query-bam-grna-db bam-db organism enzyme (:coords %)) parsed-queries)]
     (if (some f/failed? processed-queries)
       (first (filter f/failed? processed-queries))
       processed-queries)))

(defn sort-results
  "Sorts the results of a gRNA query according to the ordering
  specified in the user request map."
  [ordering grnas _]
  (case ordering
    "specificity" (sort-by :specificity grnas)
    "cutting-efficiency" (sort-by :cutting-efficiency grnas)
    (sort-by grna/num-off-targets grnas)))

(defn filter-results
  "Filters the results of a gRNA query according to the parameters
  specified in the user request map."
  [req grnas genomic-region]
  (let [[chromosone start-pos end-pos] (:coords genomic-region)
        filter-annotated (:filter-annotated (:params req))
        filter-annotated? (and (some? filter-annotated)
                               (= "true" filter-annotated))]      
    (cond->> grnas
         true (filter #(and (<= start-pos (:start %))
                           (>= end-pos (:end %))))
         filter-annotated? (filter #(not-empty (:annotations %))))))

(defn keep-only-top-n
  "Returns only the top-n grnas."
  [top-nvalue grnas]
  (vec (take top-nvalue grnas)))

(defn annotate-grnas
  "Annotates the grnas."
  [gene-annotations organism grnas genomic-region]
  (let [chr (first (:coords genomic-region))
        annotate-grna
        (fn [{start :start end :end}]
          (annotations/get-annotations gene-annotations organism chr start end))]
    (map #(assoc % :annotations (annotate-grna %)) grnas)))

(defn split-region-flanking
  [[chr start end] flanking-value]
  [{:name (str chr ":" start "-" end ":left-flank")
    :coords [chr (- start (- flanking-value 1)) start]}
   {:name (str chr ":" start "-" end ":right-flank")
    :coords [chr end (+ end (- flanking-value 1))]}])

(defn convert-regions
  "Converts genomic regions of the form,
      [chr, start, end]
  to ones of the form,
      {:name name
       :organism organism
       :coords [chr, start, end]}
  converting each region into two when
  we are in flanking mode."
  [genomic-regions organism flanking? flanking-value]
  (let [name-region #(str (nth % 0) ":" (nth % 1) "-" (nth % 2))]
    (cond->> genomic-regions
      (not flanking?) (map #(assoc {} :name (name-region %) :coords %))
      flanking?       (map #(split-region-flanking % flanking-value))
      flanking?       (apply concat)
      true            (map #(assoc % :organism organism)))))

(defn valid-req?
  [req]
  (prn req)
  (and
   (:params req)
   (or (:query-text (:params req))
       (:query-file-upload (:params req)))
   (:enzyme (:params req))
   (:organism (:params req))
   true))

(defn process-query
  "Process the query, returning either a response vector containing the
  processed gRNAs for each [chrX, start, end] input or a failure
  object with an appropriate message."
  [bam-db gene-annotations req]
  (if (valid-req? req)
    (f/attempt-all
     [genomic-regions (parse-query (:params req))
      enzyme (:enzyme (:params req))
      organism (:organism (:params req))
      topn-value (:topn-value (:params req))
      flanking-value (:flanking-value (:params req))
      topn? (and (some? topn-value)
                 (boolean (re-find #"[0-9]+" topn-value))
                 (= "true" (:topn (:params req))))
      flanking (and (some? flanking-value)
                    (boolean (re-find #"[0-9]+" flanking-value))
                    (= "true" (:flanking (:params req))))
      flanking-value-int (when flanking (Integer/parseInt flanking-value))
      converted-regions (convert-regions genomic-regions organism flanking flanking-value-int)
      vec-of-grnas (process-parsed-queries bam-db organism enzyme converted-regions)]
     (cond->> vec-of-grnas
       true (map #(annotate-grnas gene-annotations organism %2 %1) converted-regions)
       true (map #(filter-results req %2 %1) converted-regions)
       true (map #(sort-results (:ordering req) %2 %1) converted-regions)
       topn? (map #(keep-only-top-n (Integer/parseInt topn-value) %))
       true (map vector converted-regions)))
    (f/fail "Invalid POST/GET request parameters.")))
