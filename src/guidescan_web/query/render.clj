(ns guidescan-web.query.render
  "This namespace exposes the core facilities to render the results of a
  successfully processed gRNA query into various formats including
  HTML, CSV, and JSON."
  (:require [cheshire.core :as cheshire]))

(defmulti render-query-result
  (fn [format processed-query] format))
  
(defmethod render-query-result :json
  [_ processed-query]
  (cheshire/encode processed-query))

(defmethod render-query-result :csv
  [_ processed-query]
  nil)

(defn get-content-type
  [format]
  (case format
    :json "application/json"
    :csv "text/csv"))
