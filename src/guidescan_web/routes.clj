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
   extensible in the future.

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
  [req]
  (let [query (parse-query (:query (:params req)))]
    query))

(defroutes my-routes
  (POST "/foo" [] "Hello Foo")
  (GET "/bar" [] "Hello lena")
  (ANY "/query" req (query-route req)))

(def handler
  (-> my-routes
      (wrap-defaults site-defaults)))

(query-route {:params {:query "chrIV:911770-916325"
                       :genome "ce11" :enzyme "cas9"
                       :mode "within" :flank-size 1000
                       :ordering "offtargets" :display "all"
                       :top-n-results 3}})


