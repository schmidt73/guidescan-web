(ns guidescan-web.query.render
  "This namespace exposes the core facilities to render the results of a
  successfully processed gRNA query into various formats including
  HTML, CSV, and JSON."
  (:require [cheshire.core :as cheshire]
            [guidescan-web.genomics.grna :as grna]
            [clojure.data.csv :as csv]))

(def csv-header
  ["Region-name" "gRNA-ID" "gRNA-Seq" "Target-Seq" "PAM"
   "Number of off-targets" "Off-target summary" "Cutting efficiency"
   "Specificity" "Coordinates" "Strand"])

(defn revcom
  [sequence]
  (-> (map {\A \T \T \A \G \C \C \G \N \N} sequence)
      (reverse)
      (clojure.string/join)))

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
     coords direction]))

(defn processed-query-to-csv-vector
  [[genomic-region grnas]]
  (map-indexed #(grna-to-csv-vector genomic-region %2 %1) grnas))

(defn grna-to-bed-line
  [chr grna]
  (let [direction (if (= (:direction grna) :positive) "+" "-")
        name (str chr ":" (:start grna) "-" (:end grna))]
    (clojure.string/join
     "\t"
     [chr (:start grna) (:end grna) name "0" direction])))

(defn processed-query-to-bed-entry
  [[genomic-region grnas]]
  (->> grnas
   (map #(grna-to-bed-line (first (:coords genomic-region)) %))
   (clojure.string/join "\n")))

(defmulti render-query-result
  (fn [format processed-query] format))
  
(defmethod render-query-result :json
  [_ processed-query]
  (cheshire/encode processed-query))

(defmethod render-query-result :bed
  [_ processed-query]
  (->> processed-query
   (map processed-query-to-bed-entry)
   (clojure.string/join "\n")
   (str "track name=\"guideRNAs\"\n")))

(defmethod render-query-result :csv
  [_ processed-query]
  (with-out-str
    (csv/write-csv
     *out*
     (reduce into [csv-header]
             (mapv processed-query-to-csv-vector
                   processed-query)))))

(defn get-content-type
  [format]
  (case format
    :json "application/json"
    :bed "text/bed"
    :csv "text/csv"))
