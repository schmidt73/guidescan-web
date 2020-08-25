(ns guidescan-web.routes
  "This namespace contains the core logic for routing in Guidescan.

   It exposes a REST api that returns a well formated list of guideRNAs
   pertaining to each input gene."
  (:require
   [ring.middleware.defaults :refer :all]
   [compojure.core :refer :all]
   [guidescan-web.bam.db :as db]))

(defn failure?
  [parse-tree]
  (contains? parse-tree :failure))

(defn success?
  [parse-tree]
  (contains? parse-tree :success))

(defn- parse-line
  "Parses one line of a text file, returning a parse object indicating
   success or failure along with an error message."
  [line-number line]
  (if-let [[_ chr start-str end-str] (re-find #"(chr.*):(\d+)-(\d+)" line)]
    {:success
      [chr (Integer/parseInt start-str) (Integer/parseInt end-str)]}
    {:failure (str "Failed to parse: \"" line "\" on line " (+ 1 line-number))}))

(defn parse-raw-text
  "Parses the raw text of a query, either coming
   from a text file or from a query box."
  [text]
  (let* [parsed-lines
         (->> (clojure.string/split-lines text)
              (map-indexed parse-line)) 
         failed-lines (filter failure? parsed-lines)]
    (if (not-empty failed-lines)
      (first failed-lines)
      {:success (map :success parsed-lines)})))

(defn get-query-type
  "Returns the type of query."
  [req-params]
  (if-let [filename (get-in req-params [:query-file-upload :filename])]
    (if (not= filename "")
      (cond
        (re-find #"(?i).*\.txt" filename)           :text-file
        (re-find #"(?i).*\.fasta" filename)         :fasta-file
        (re-find #"(?i).*\.((gtf)|(gff))" filename) :gtf-file
        (re-find #"(?i).*\.bed" filename)           :bed-file
        :otherwise                                  :unknown-file)
      :text)
    :text))

(defmulti parse-query
  "Parses the parameters of a query for easy look up in the guideRNA
   database. The multimethod dispatches on the type of query, and it
   handles raw text input, BED files, GTF files, and FASTA files.

   The method returns a map indicating either a successful parse tree
   or failure to parse along with an error message.

   A succesful parse tree looks like this:
   [[chrX1 start-1 end-1] [chrX2 start-2 end-2] ...]"
  get-query-type)

(defmethod parse-query :text
  [req-params]
  (parse-raw-text (:query-text req-params)))

(defmethod parse-query :text-file
  [req-params]
  (parse-raw-text (slurp (get-in req-params [:query-file-upload :tempfile]))))

(defn query-route
  "Core of the Guidescan website. Exposes a REST api that takes a
   query in a variety of forms, parses it, and returns the response
   as a nested JSON object."
  [config req]
  (let [result (parse-query (:params req))
        organism (:organism (:params req))]
    (if-let [query (:success result)]
      (->> (map #(apply (partial db/query-bam config organism) %) query)
           (map #(clojure.string/join "\n" %)) 
           (clojure.string/join "\n\n\n"))
      (:failure result))))

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
