(ns guidescan-web.query.render
  "This namespace exposes the core facilities to render the results of a
  successfully processed gRNA query into various formats including
  HTML, CSV, and JSON."
  (:require [cheshire.core :as cheshire]
            [guidescan-web.genomics.grna :as grna]
            [clojure.data.csv :as csv]))

(def csv-header
  ["Coordinates" "Sequence" "Number of off-targets"
   "Off-target summary" "Cutting efficiency" "Specificity"])

(defn grna-to-csv-vector [chr grna]
  (let [direction (if (= (:direction grna) :positive) "+" "-")
        coords (str chr ":" (:start grna) "-" (:end grna) ":"
                    direction)
        num-ots (grna/num-off-targets grna)
        ots (str "2:" (grna/num-off-targets grna 2) " | "
                 "3:" (grna/num-off-targets grna 3))] 
        
    [coords (:sequence grna) num-ots ots
     (:cutting-efficiency grna) (:specificity grna)]))

(defn processed-query-to-csv-vector
  [[[chr start end] grnas]]
  (into
   [[(str chr ":" start "-" end)]
    csv-header]
   (mapv #(grna-to-csv-vector chr %) grnas)))

(defmulti render-query-result
  (fn [format processed-query] format))
  
(defmethod render-query-result :json
  [_ processed-query]
  (cheshire/encode processed-query))

(defmethod render-query-result :csv
  [_ processed-query]
  (with-out-str
    (csv/write-csv
     *out*
     (reduce into []
             (mapv processed-query-to-csv-vector
                   processed-query)))))

(defn get-content-type
  [format]
  (case format
    :json "application/json"
    :csv "text/csv"))
