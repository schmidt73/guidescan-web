(ns guidescan-web.query.process
  "This namespace exposes an interface for processing gRNA queries,
  applying appropriate filters and sorting them correctly so that they
  are suitable for rendering."
  (:require [guidescan-web.bam.db :as db]
            [guidescan-web.genomics.grna :as grna]
            [guidescan-web.genomics.annotations :as annotations]
            [guidescan-web.query.parsing :refer [parse-request]]
            [guidescan-web.utils :refer [revcom]]
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
  [{:keys [cutting-efficiency-bounds specificity-bounds filter-annotated]}
   grnas
   genomic-region]
  (let [[chromosome start-pos end-pos] (:coords genomic-region)
        within (fn [k {:keys [lower upper]}]
                 (fn [grna]
                   (if-let [v (k grna)]
                     (and (<= lower v) (>= upper v))
                     true)))]
    (cond->> grnas
      true (filter #(and (<= start-pos (:start %))
                         (>= end-pos (:end %))))
      cutting-efficiency-bounds (filter (within :cutting-efficiency cutting-efficiency-bounds))
      specificity-bounds (filter (within :specificity specificity-bounds))
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
  [{[chr start end] :coords r :region-name} flanking-value]
  [{:region-name (str r ":left-flank")
    :coords [chr (- start (- flanking-value 1)) start]}
   {:region-name (str r ":right-flank")
    :coords [chr end (+ end (- flanking-value 1))]}])

(defn convert-regions
  "Converts genomic regions of the form,
      {:region-name name, :coords [chr, start, end]}
  to ones of the form,
      {:region-name name
       :organism organism
       :coords [chr, start, end]}
  converting each region into two when
  we are in flanking mode."
  [genomic-regions organism flanking]
  (cond->> genomic-regions
    flanking       (map #(split-region-flanking % flanking))
    flanking       (apply concat)
    true           (map #(assoc % :organism organism))))

(defn- wrap-result
  [query-type result]
  {:query-type query-type
   :result result})

(defmulti process-query
  "Process the query, returning a map of the form:

     {:query-type t :result r}

  On success or a failure object with an appropriate
  message."
  (fn [_ req] (keyword (get-in req [:params :query-type] :standard))))

(defmethod process-query :standard
  [{:keys [bam-db gene-annotations gene-resolver sequence-resolver]}
   req]
  (f/attempt-all
   [{:keys [genomic-regions
            enzyme
            organism
            filter-annotated
            topn
            cutting-efficiency-bounds
            specificity-bounds
            flanking]}
    (parse-request :standard
                   {:gene-resolver gene-resolver :sequence-resolver sequence-resolver}
                   req)
    converted-regions (convert-regions genomic-regions organism flanking)
    vec-of-grnas (process-parsed-queries bam-db organism enzyme converted-regions)
    filter-opts {:filter-annotated filter-annotated
                 :cutting-efficiency-bounds cutting-efficiency-bounds
                 :specificity-bounds specificity-bounds}]
   (cond->> vec-of-grnas
     true (map #(annotate-grnas gene-annotations organism %2 %1) converted-regions)
     true (map #(filter-results filter-opts %2 %1) converted-regions)
     true (map #(sort-results "num-off-targets" %2 %1) converted-regions)
     (some? topn) (map #(keep-only-top-n topn %))
     true (map vector converted-regions)
     true (wrap-result :standard))))

(defn- find-grna
  [grna intersecting-grnas]
  (let [hamming-distance #(count (filter identity (map = %1 %2)))]
    (first
     (filter
       #(or (>= 17 (hamming-distance (:region-name grna) (:sequence %)))
            (>= 17 (hamming-distance (revcom (:region-name grna)) (:sequence %))))
       intersecting-grnas))))

(defmethod process-query :grna
  [{:keys [bam-db gene-annotations sequence-resolver]}
   req]
  (f/attempt-all
   [{:keys [enzyme
            organism
            genomic-regions]}
    (parse-request :grna {:sequence-resolver sequence-resolver} req)
    good-genomic-regions (filter #(not (:error %)) genomic-regions)
    bad-genomic-regions (filter :error genomic-regions)
    converted-regions (convert-regions good-genomic-regions organism false)
    vec-of-grnas (process-parsed-queries bam-db organism enzyme converted-regions)]
   (->>
     (map find-grna good-genomic-regions vec-of-grnas)
     (map #(assoc %2 :chr (get-in %1 [:coords 0])) good-genomic-regions)
     (concat bad-genomic-regions)
     (wrap-result :grna))))

