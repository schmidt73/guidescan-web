(ns guidescan-web.query.process
  "This namespace exposes an interface for processing gRNA queries,
  applying appropriate filters and sorting them correctly so that they
  are suitable for rendering."
  (:require [guidescan-web.bam.db :as db]
            [guidescan-web.genomics.grna :as grna]
            [guidescan-web.genomics.annotations :as annotations]
            [guidescan-web.query.parsing :refer [parse-request]]
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
  [filter-annotated grnas genomic-region]
  (let [[chromosone start-pos end-pos] (:coords genomic-region)]
    (cond->> grnas
      true (filter #(and (<= start-pos (:start %))
                         (>= end-pos (:end %))))
      filter-annotated (filter #(not-empty (:annotations %))))))

(defn keep-only-top-n
  "Returns only the top-n grnas."
  [top-nvalue grnas]
  (vec (take top-nvalue grnas)))

(defn annotate-grnas
  "Annotates the grnas."
  [gene-annotations organism grnas genomic-region]
  (let [cut-offset 6
        chr (first (:coords genomic-region))
        annotate-grna
        (fn [{start :start end :end rna :sequence dir :direction}]
          (let [check-pos (if (= dir :positive) (- end cut-offset) (+ start cut-offset))]
            (annotations/get-annotations gene-annotations organism chr check-pos check-pos)))]
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
  [genomic-regions organism flanking]
  (let [name-region #(str (nth % 0) ":" (nth % 1) "-" (nth % 2))]
    (cond->> genomic-regions
      (not flanking) (map #(assoc {} :name (name-region %) :coords %))
      flanking       (map #(split-region-flanking % flanking))
      flanking       (apply concat)
      true           (map #(assoc % :organism organism)))))

(defn process-query
  "Process the query, returning either a response vector containing the
  processed gRNAs for each [chrX, start, end] input or a failure
  object with an appropriate message."
  [bam-db gene-annotations gene-resolver req]
  (f/attempt-all
   [{genomic-regions  :genomic-regions
     enzyme           :enzyme
     organism         :organism
     filter-annotated :filter-annotated
     topn             :topn
     flanking         :flanking} (parse-request gene-resolver req)
    converted-regions (convert-regions genomic-regions organism flanking)
    vec-of-grnas (process-parsed-queries bam-db organism enzyme converted-regions)]
   (cond->> vec-of-grnas
     true (map #(annotate-grnas gene-annotations organism %2 %1) converted-regions)
     true (map #(filter-results filter-annotated %2 %1) converted-regions)
     true (map #(sort-results "num-off-targets" %2 %1) converted-regions)
     (some? topn) (map #(keep-only-top-n topn %))
     true (map vector converted-regions))))
