(ns guidescan-web.query.render
  "This namespace exposes the core facilities to render the results of a
  successfully processed gRNA query into various formats including
  HTML, CSV, and JSON."
  (:require [cheshire.core :as cheshire]
            [guidescan-web.genomics.grna :as grna]
            [guidescan-web.utils :refer [revcom]]
            [failjure.core :as f]
            [dk.ative.docjure.spreadsheet :as excel]
            [clojure.data.csv :as csv]))


(def csv-header
  ["Region-name" "gRNA-ID" "gRNA-Seq" "Target-Seq" "PAM"
   "Number of off-targets" "Off-target summary" "Cutting efficiency"
   "Specificity" "Rank" "Coordinates" "Strand"])

(def library-csv-header
  ["Gene_Symbol" "gRNA_ID" "gRNA_Seq" "Library_Oligo"
   "Standard BsmBI Forward Oligo" "Standard BsmBI Reverse Oligo"
   "Pool_Id" "Adapter Name" "Chromosome" "Position" "Strand"
   "Cfd_Score" "Native_Score" "Source" "Category"])

(defn library-guide-to-csv-vector
  [pool-num gene-sym category idx guide]
  [gene-sym (str gene-sym "." (inc idx) "." (:libraries/source guide))
   (:libraries/grna guide) (:library_oligo guide)
   (:forward_oligo guide) (:reverse_oligo guide)
   (inc pool-num)
   (:adapter_name guide) (get-in guide [:coords :chr] "NA")
   (get-in guide [:coords :pos] "NA") (get-in guide [:coords :strand] "NA")
   (:libraries/cfd_score guide) (:libraries/native_score guide)
   (:libraries/source guide) (str category)])

(defn library-to-csv-vector
  [idx pool]
  (apply
   concat
   (mapv
    (fn [entry]
      (let [category (or (:type entry) (if (:gene entry) :gene :essential))
            gene-symbol (case category
                          :gene (:gene entry)
                          :essential (get-in entry [:essential-gene :essential_genes/gene_symbol])
                          :controls "Control")]
        (map-indexed
         #(library-guide-to-csv-vector idx gene-symbol category %1 %2)
         (:guides entry))))
    pool)))

(defn grna-to-csv-vector [genomic-region grna idx]
  (let [direction (if (= (:direction grna) :positive) "+" "-")
        coords (str (first (:coords genomic-region)) ":" (:start grna) "-" (:end grna))
        pam (if (= direction "+")
              (subs (:sequence grna) 20 23)
              (revcom (subs (:sequence grna) 0 3)))
        grna-seq  (if (= direction "+")
                    (subs (:sequence grna) 0 20)
                    (revcom (subs (:sequence grna) 3 23)))
        target-seq  (if (= direction "+")
                      (subs (:sequence grna) 0 20)
                      (subs (:sequence grna) 3 23))
        num-ots (grna/num-off-targets grna)
        ots (str "2:" (grna/num-off-targets grna 2) " | "
                 "3:" (grna/num-off-targets grna 3))
        cutting-efficiency (get grna :cutting-efficiency "N/A")
        specificity (get grna :specificity "N/A")] 
    [(:region-name genomic-region)
     (format "%s.%d" (:region-name genomic-region) idx)
     grna-seq target-seq pam num-ots
     ots cutting-efficiency specificity
     (inc idx) coords direction]))

(defn processed-query-to-csv-vector
  [[genomic-region grnas]]
  (map-indexed #(grna-to-csv-vector genomic-region %2 %1) grnas))

(defn grna-to-bed-line
  [chr grna]
  (let [[direction start end]
        (if (= (:direction grna) :positive)
          ["+" (:start grna) (:end grna)]
          ["-" (dec (:start grna)) (dec (:end grna))])
        name (str chr ":" (:start grna) "-" (:end grna))]
    (clojure.string/join
     "\t"
     [chr start end name "0" direction])))

(defn processed-query-to-bed-entry
  [[genomic-region grnas]]
  (->> grnas
   (map #(grna-to-bed-line (first (:coords genomic-region)) %))
   (clojure.string/join "\n")))

(defmulti render-query-result
  (fn [format processed-query] [format (:query-type processed-query)]))
  
(defmethod render-query-result [:json :standard]
  [_ processed-query]
  (cheshire/encode
   (map (fn [[query grnas]]
          [query
           (map #(if (= (:direction %) :negative)
                   (update % :sequence revcom)
                   %)
                grnas)])
        (:result processed-query))))

(defmethod render-query-result [:bed :standard]
  [_ processed-query]
  (->> (:result processed-query)
   (map processed-query-to-bed-entry)
   (clojure.string/join "\n")
   (str "track name=\"guideRNAs\"\n")))

(defmethod render-query-result [:csv :standard]
  [_ processed-query]
  (with-out-str
    (csv/write-csv
     *out*
     (reduce into [csv-header]
             (mapv processed-query-to-csv-vector
                   (:result processed-query))))))

(defmethod render-query-result [:excel :standard]
  [_ processed-query]
  (with-open [out-stream (new java.io.ByteArrayOutputStream)]
    (->> (excel/create-workbook
          "gRNAs"
          (reduce into [csv-header]
             (mapv processed-query-to-csv-vector
                   (:result processed-query))))
         (excel/save-workbook-into-stream! out-stream))
    (.toByteArray out-stream)))

(defmethod render-query-result [:json :grna]
  [_ processed-query]
  (cheshire/encode
   (map (fn [grna]
          (if (= (:direction grna) :negative)
              (update grna :sequence revcom)
              grna))
        (:result processed-query))))

(defmethod render-query-result [:json :library]
  [_ processed-query]
  (cheshire/encode (:result processed-query)))

(defmethod render-query-result [:csv :library]
  [_ processed-query]
  (let [library (get-in processed-query [:result :library])
        failed-genes (get-in processed-query [:result :failed-genes])
        library-rows (map-indexed library-to-csv-vector library) 
        failed-rows (mapv #(vector (f/message %)) failed-genes)]
    (with-out-str
      (->> (concat
            [library-csv-header]
            (vec (apply concat library-rows))
            [[""] ["Failed Genes"]]
            failed-rows)
           (csv/write-csv *out*)))))

(defmethod render-query-result [:excel :library]
  [_ processed-query]
  (let [library (get-in processed-query [:result :library])
        failed-genes (get-in processed-query [:result :failed-genes])
        library-rows (map-indexed library-to-csv-vector library) 
        failed-rows (mapv #(vector (f/message %)) failed-genes)]
    (with-open [out-stream (new java.io.ByteArrayOutputStream)]
      (->> (excel/create-workbook
            "Library"
            (concat [library-csv-header]
                    (vec (apply concat library-rows)))
            "Failed Genes"
            (concat [["Failed Genes"]] failed-rows))
           (excel/save-workbook-into-stream! out-stream))
      (.toByteArray out-stream))))

(defmethod render-query-result :default
  [_ _]
  (cheshire/encode {:error "Render format not supported for query type."}))

(defn get-content-type
  [format]
  (case format
    :json "application/json"
    :bed "text/bed"
    :csv "text/csv"
    :excel "application/vnd.ms-excel"))
