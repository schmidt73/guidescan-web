(ns guidescan-web.routes
  "This namespace contains the core logic for routing in Guidescan.

   It exposes a REST api that returns a well formated list of guideRNAs
   pertaining to each input gene."
  (:require
   [ring.middleware.defaults :refer :all]
   [compojure.core :refer :all]
   [guidescan-web.bam.db :as db]))

(defn- parse-row
  [[chr start-str end-str & _]]
  [chr (Integer/parseInt start-str) (Integer/parseInt end-str)])
 
(defmulti parse-query
  "Parses the query for easy look up in the guideRNA database. The
   multimethod dispatches on the type of query, so it should handle raw
   text input, BED files, GFF files, and FASTA files while being
   extensible in the future. Raises a ParsingException on failure to
   parse input.

   The method returns output with the following form:
   [[chrX1 start-1 end-1] [chrX2 start-2 end-2] ...]"
  (fn [req] :text))
 
(defmethod parse-query :text
  [text]
  (->> text
       (clojure.string/split-lines)
       (map #(rest (re-find #"(chr.*):(\d+)-(\d+)" %)))
       (map parse-row)))

(defn query-route
  "Core of the Guidescan website. Exposes a REST api that takes a
   query in a variety of forms, parses it, and returns the response
   as a nested JSON object."
  [config req]
  (try
    (catch Parse))
  (let [query (parse-query (:query (:params req)))
        organism (:organism (:params req))]
    (->> (map #(apply (partial db/query-bam config organism) %) query)
         (map #(clojure.string/join "\n" %)) 
         (clojure.string/join "\n\n\n"))))

(defn create-routes
  [config]
  (routes
   (ANY "/query" req (query-route config req))
   (GET "/" [] ())))

(def www-defaults
  (-> site-defaults
    (assoc-in [:static :resources] "static")
    (assoc-in [:security :anti-forgery] false)))

(defn wrap-dir-index [handler]
  (fn [req]
    (handler
     (update-in req [:uri]
                #(if (= "/" %) "/index.html" %)))))

(defn handler [config]
  (-> (create-routes config)
      (wrap-defaults www-defaults)
      (wrap-dir-index)))
