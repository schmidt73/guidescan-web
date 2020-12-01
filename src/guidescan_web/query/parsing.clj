(ns guidescan-web.query.parsing
  "This namespace exposes an interface for parsing raw Ring request
  objects into a format suitable for processing.

  It assumes the spec located here for BED files:
    - https://m.ensembl.org/info/website/upload/bed.html
  It assumes the spec located here for GFF/GTF files:
    - https://uswest.ensembl.org/io/website/upload/gff.html"
  (:require [failjure.core :as f]
            [guidescan-web.genomics.resolver :as resolver]))

(defn- name-region [[chr start end :as coord]]
  {:region-name (str chr ":" start "-" end)
   :coords coord})

(defn- parse-req-bool
  "Parses a boolean out of the request parameters, returning a Failure
  object when the key is not in the params."
  [params-key req]
  (if-let [val (get (:params req) params-key)]
    (= "true" val)
    (f/fail "Boolean %s not found in request parameters." (str params-key)))) 

(defn- parse-req-int
  "Parses an integer out of the request parameters, returning Failure
  when the key is not in params or is not an integer."
  [params-key req]
  (if-let [val (get (:params req) params-key)]
    (if-let [match (re-find #"^[0-9]+$" val)]
      (Integer/parseInt match)
      (f/fail "Parameter %s is integral." (str params-key)))
    (f/fail "Integer %s not found in request parameters." (str params-key)))) 

(defn- parse-req-string
  "Parses a string out of the request parameters, returning a Failure
  object when the key is not in the params."
  [params-key req]
  (if-let [val (get (:params req) params-key)]
    val
    (f/fail "String %s not found in request parameters." (str params-key))))

(defn parse-bounds
  [params-key-l params-key-u req]
  (let [lower-s (get (:params req) params-key-l)
        upper-s (get (:params req) params-key-u)]
    (if (and lower-s upper-s)
      (f/try-all [lower (Float/parseFloat lower-s)
                  upper (Float/parseFloat upper-s)]
        {:upper upper :lower lower}
        (f/fail "Parameters %s and %s are floating point." params-key-l params-key-u))
      (f/fail "Parameters %s and %s not found." params-key-l params-key-u))))

(defn- parse-entrez-id
  [gene-resolver organism text]
  (if-let [[_ entrez-id-str] (re-find #"^(\d+)" text)]
    (if-let [gene (resolver/resolve-entrez-id
                   gene-resolver organism (Integer/parseInt entrez-id-str))]
      {:region-name (:genes/gene_symbol gene)
       :coords
       [(str "chr" (:chromosomes/name gene))
        (:genes/start_pos gene) (:genes/end_pos gene)]})))

(defn- parse-gene-symbol
  [gene-resolver organism text]
  (if-let [gene (resolver/resolve-gene-symbol gene-resolver organism text)]
    {:region-name (:genes/gene_symbol gene)
     :coords
     [(str "chr" (:chromosomes/name gene))
      (:genes/start_pos gene) (:genes/end_pos gene)]}))

(defn- parse-rna-sequence
  [gene-resolver organism text]
  (let [rna-seq (-> (clojure.string/upper-case text)
                    (clojure.string/trim-newline)
                    (clojure.string/trim))
        rna-len (count rna-seq)
        region-name (if (> rna-len 30)
                      (str (subs rna-seq 0 10)
                           "..."
                           (subs rna-seq (- rna-len 11) (- rna-len 1)))
                      rna-seq)]
    (if (re-matches #"^[ATCGRN]+$" rna-seq)
      (if-let [coords (resolver/resolve-sequence gene-resolver organism rna-seq)]
        {:region-name region-name
         :coords
         [(str "chr" (:chromosome coords)) (:position coords) (+ (:position coords) rna-len)]}))))

(defn- parse-chromosome
  [text]
  (if-let [[_ chr start-str end-str] (re-find #"^(chr.*):(\d+)-(\d+)" text)]
    (name-region
     [chr (Integer/parseInt start-str) (Integer/parseInt end-str)])))

(defn- parse-line
  "Parses one line of a text file, returning a parse tree indicating
  success or failure along with an error message."
  [gene-resolver organism line-number line]
  (or (parse-chromosome line)
      (parse-gene-symbol gene-resolver organism line)
      (parse-entrez-id gene-resolver organism line)
      (parse-rna-sequence gene-resolver organism line)
      (f/fail (str "Failed to parse: \"%s\" on line %d\n"
                   "Line must be either of format \"chrX:start-end\","
                   " a known gene symbol, a known Entrez GeneID, or"
                   " the sequence of a genomic locus.")
              line (+ 1 line-number))))

(defn- parse-gtf-line
  "Parses one line of a .gtf file, returning a parse tree indicating
  success or failure along with an error message."
  [line-number line]
  (if-let [[_ chr start-str end-str] (re-find #"^(.+)\t.+\t.+\t(\d+)\t(\d+)\t.+\t.+\t.+\t.*" line)]
    (name-region [chr (- (Integer/parseInt start-str) 1) (Integer/parseInt end-str)])
    (if (re-find #"(?i)track(\s|$).*" line)
      :skip
      (f/fail (str "Invalid GTF row: \"" line "\" on line " (+ 1 line-number))))))

(defn- parse-bed-line
  "Parses one line of .bed file, returning a parse tree indicating
  success or failure along with an error message."
  [line-number line]
  (if-let [[_ chr start-str end-str] (re-find #"^(\S+)\s+(\d+)\s+(\d+)(\s|$).*" line)]
    (name-region [chr (Integer/parseInt start-str) (Integer/parseInt end-str)])
    (if (re-find #"(?i)(track|browser)(\s|$).*" line)
      :skip
      (f/fail (str "Failed to parse: \"" line "\" on line " (+ 1 line-number))))))

(defn- parse-raw-text
  "Parses the raw text line by line using the passed in
  line-parser. Returns the first error if any exists."
  [text line-parser]
  (let* [parsed-lines
         (->> (clojure.string/split-lines text)
              (map-indexed line-parser)
              (remove #(= :skip %))) 
         failed-lines (filter f/failed? parsed-lines)]
    (if (not-empty failed-lines)
      (f/fail (clojure.string/join "\n" (map f/message failed-lines)))
      parsed-lines)))

(defn get-query-type
  "Returns the type of query."
  [_ _ req]
  (if-let [filename (get-in req [:params :query-file-upload :filename])]
    (if (not= filename "")
      (cond
        (re-find #"(?i).*\.txt" filename)           :text-file
        (re-find #"(?i).*\.fasta" filename)         :fasta-file
        (re-find #"(?i).*\.((gtf)|(gff))" filename) :gtf-file
        (re-find #"(?i).*\.bed" filename)           :bed-file
        :otherwise                                  :unknown-file)
      :text)
    :text))

(defmulti parse-genomic-regions
  "Parses the body of a query into a list of genomic regions in 0 based
  half-open coordinate format. The multimethod dispatches on the type
  of query, and it handles raw text input, BED files, GTF files, and
  FASTA files.

  The method returns either a successful parse tree or a Failure
  object along with an error message.

  A succesful parse tree looks like this:
  [[chrX1 start-1 end-1] [chrX2 start-2 end-2] ...]"
  get-query-type)

(defmethod parse-genomic-regions :text
  [gene-resolver organism req]
  (f/if-let-ok? [query-text (parse-req-string :query-text req)]
    (parse-raw-text query-text (partial parse-line gene-resolver organism))))

(defmethod parse-genomic-regions :text-file
  [gene-resolver organism req]
  (let [text (slurp (get-in req [:params :query-file-upload :tempfile]))]
    (parse-raw-text text (partial parse-line gene-resolver organism))))

(defmethod parse-genomic-regions :bed-file
  [_ _ req]
  (let [text (slurp (get-in req [:params :query-file-upload :tempfile]))]
    (parse-raw-text text parse-bed-line)))

(defmethod parse-genomic-regions :gtf-file
  [_ _ req]
  (let [text (slurp (get-in req [:params :query-file-upload :tempfile]))]
    (parse-raw-text text parse-gtf-line)))

(defmethod parse-genomic-regions :unknown-file
  [_ _ _]
  (f/fail "Unknown file type."))

(defmethod parse-genomic-regions :fasta-file
  [_ _ _]
  (f/fail "Unsupported file type .fasta"))

(defn failure-vector-into-map
  "Converts a vector of key-value pairs into a map, shortcircuting
  failure if any value is both a failure object and flagged as
  required.

  Input is a vector with elements of the form:
    [key failure-value (:required|:optional)]"
  [failure-vector]
  (if-let [failure (some #(and (f/failed? (second %)) (= :required (nth % 2)) (second %))
                         failure-vector)]
    failure
    (reduce #(assoc %1 (first %2) (second %2)) {}
            (remove #(f/failed? (second %)) failure-vector))))

(defn parse-request
  "Parses the raw request object into a format suitable for processing
  or returns a Failure object along with an error message.

  On success, the returned map has the following structure:
     {:genomic-regions [{:region-name X1 :coords [chrX1 start-1 end-1]}
                        {:region-name X2 :coords [chrX2 start-2 end-2]} ...]
      :enzyme STRING
      :organism STRING
      :filter-annotated BOOL          OPTIONAL
      :topn INT                       OPTIONAL
      :flanking INT                   OPTIONAL}"
  [gene-resolver req]
  (f/attempt-all
   [parsed-request
    (failure-vector-into-map
     [[:enzyme (parse-req-string :enzyme req) :required]
      [:organism (parse-req-string :organism req) :required]
      [:filter-annotated (parse-req-bool :filter-annotated req) :optional]
      [:topn (parse-req-int :topn req) :optional]
      [:flanking (parse-req-int :flanking req) :optional]
      [:cutting-efficiency-bounds (parse-bounds :ce-bounds-l :ce-bounds-u req) :optional]
      [:specificity-bounds (parse-bounds :s-bounds-l :s-bounds-u req) :optional]])
    genomic-regions (parse-genomic-regions gene-resolver (:organism parsed-request) req)]
   (assoc parsed-request :genomic-regions genomic-regions)))
