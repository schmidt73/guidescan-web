(ns guidescan-web.query.parsing
  "This namespace exposes an interface for parsing raw Ring request
  objects into a format suitable for processing.

  It assumes the spec located here for BED files:
    - https://m.ensembl.org/info/website/upload/bed.html
  It assumes the spec located here for GFF/GTF files:
    - https://uswest.ensembl.org/io/website/upload/gff.html

  There are multiple types of requests that are parsed by this
  module:
    1. Standard requests (genomic region requests)
    2. Library creation requests
    3. gRNA validation requests"
  (:require [failjure.core :as f]
            [guidescan-web.genomics.resolver :as resolver]))

(defn- name-region [chr-name [chr start end :as coord]]
  {:region-name (str "chr" chr-name ":" start "-" end)
   :chromosome-name (str "chr" chr-name)
   :coords coord})

(defn- parse-req-bool
  "Parses a boolean out of the request parameters, returning a Failure
  object when the key is not in the params."
  [params-key req]
  (if-let [val (get (:params req) params-key)]
    (= "true" val)
    (f/fail "Boolean %s not found in request parameters." (str params-key)))) 

(defn- parse-req-decimal-range
  "Parses a decimal out of the request parameters, returning Failure
  when the key is not in params or is not an float."
  [start end params-key req]
  (if-let [val (get (:params req) params-key)]
    (try
      (let [d (Double/parseDouble val)]
        (if (and (>= d start) (<= d end))
          d
          (f/fail "Parameter %s is outside range [%d, %d]." (str params-key) start end)))
      (catch java.lang.NumberFormatException e
        (f/fail "Parameter %s is decimal." (str params-key))))
    (f/fail "Decimal %s not found in request parameters." (str params-key))))

(defn- parse-req-int-range
  "Parses an integer out of the request parameters within a certain
  range [start, end], returning Failure when the key is not in params
  or is not an integer or is out of range."
  [start end params-key req]
  (if-let [val (get (:params req) params-key)]
    (if-let [match (re-find #"^[0-9]+$" val)]
      (let [x (Integer/parseInt match)]
        (if (and (>= x start) (<= x end))
          x
          (f/fail "Parameter %s is outside range [%d, %d]." (str params-key) start end)))
      (f/fail "Parameter %s is integral." (str params-key)))
    (f/fail "Integer %s not found in request parameters." (str params-key)))) 

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
       :chromosome-name (str "chr" (:chromosomes/name gene))
       :coords
       [(:chromosomes/accession gene)
        (:genes/start_pos gene) (:genes/end_pos gene)]})))

(defn- parse-gene-symbol
  [gene-resolver organism text]
  (if-let [gene (resolver/resolve-gene-symbol gene-resolver organism text)]
    {:region-name (:genes/gene_symbol gene)
     :chromosome-name (str "chr" (:chromosomes/name gene))
     :coords
     [(:chromosomes/accession gene)
      (:genes/start_pos gene) (:genes/end_pos gene)]}))

(defn- parse-chromosome
  [gene-resolver organism text]
  (let [text (clojure.string/replace text "," "")]
    (if-let [[_ chr start-str end-str] (re-find #"^chr(.*):(\d+)-(\d+)" text)]
      (if-let [accession (resolver/resolve-chromosome-name gene-resolver organism chr)]
        (name-region chr
         [accession (Integer/parseInt start-str) (Integer/parseInt end-str)])))))

(defn- parse-line
  "Parses one line of a text file, returning a parse tree indicating
  success or failure along with an error message."
  [{:keys [gene-resolver]} organism line-number line]
  (let [line (clojure.string/trim line)]
    (or (parse-chromosome gene-resolver organism line)
        (parse-gene-symbol gene-resolver organism line)
        (parse-entrez-id gene-resolver organism line)
        (f/fail (str "Failed to parse: \"%s\" on line %d\n"
                     "Line must be either of format \"chrX:start-end\","
                     " a known gene symbol, or a known Entrez GeneID.")
                line (+ 1 line-number)))))

(defn- parse-gtf-line
  "Parses one line of a .gtf file, returning a parse tree indicating
  success or failure along with an error message."
  [{:keys [gene-resolver]} organism line-number line]
  (if-let [[_ chr start-str end-str] (re-find #"^chr(.+)\t.+\t.+\t(\d+)\t(\d+)\t.+\t.+\t.+\t.*" line)]
    (if-let [accession (resolver/resolve-chromosome-name gene-resolver organism chr)]
      (name-region chr [accession (- (Integer/parseInt start-str) 1) (Integer/parseInt end-str)])
      (f/fail "Failed to resolve chromsome " chr " on line " (+ 1 line-number)))
    (if (re-find #"(?i)track(\s|$).*" line)
      :skip
      (f/fail (str "Invalid GTF row: \"" line "\" on line " (+ 1 line-number))))))

(defn- parse-bed-line
  "Parses one line of .bed file, returning a parse tree indicating
  success or failure along with an error message."
  [{:keys [gene-resolver]} organism line-number line]
  (if-let [[_ chr start-str end-str] (re-find #"^chr(\S+)\s+(\d+)\s+(\d+)(\s|$).*" line)]
    (if-let [accession (resolver/resolve-chromosome-name gene-resolver organism chr)]
      (name-region chr [accession (Integer/parseInt start-str) (Integer/parseInt end-str)])
      (f/fail "Failed to resolve chromsome " chr " on line " (+ 1 line-number)))
    (if (re-find #"(?i)(track|browser)(\s|$).*" line)
      :skip
      (f/fail (str "Failed to parse: \"" line "\" on line " (+ 1 line-number))))))

(defn- parse-raw-text
  "Parses the raw text line by line using the passed in
  line-parser. Returns only the successfully parsed lines if stop-at-error?
  is false."
  ([text line-parser stop-at-error?]
   (let* [parsed-lines
          (->> (clojure.string/split-lines text)
               (map-indexed line-parser)
               (remove #(= :skip %))) 
          failed-lines (filter f/failed? parsed-lines)
          successful-lines (filter f/ok? parsed-lines)]
     (if (and stop-at-error? (not-empty failed-lines))
         (f/fail (clojure.string/join "\n" (map f/message failed-lines)))
         (if (empty? successful-lines)
           (f/fail (clojure.string/join "\n" (map f/message failed-lines)))
           successful-lines))))
  ([text line-parser]
   (parse-raw-text text line-parser true)))

(defn- convert-coords
  [seq {:keys [accession chr distance pos strand]}]
  (let [[start end] (if (= strand "-")
                      [(- pos (count seq)) pos]
                      [pos (+ pos (count seq))])]
    {:region-name seq
     :chromosome-name (str "chr" chr)
     :coords [accession start end]}))

(defn- dna-seq?
  [text]
  (re-matches #"^[atcgnATCGN\\R]+$" text))

(defn- parse-dna-sequence
  "Parses the text as a DNA sequence, resolving its coordinates using
  the sequence-resolver component."
  [{:keys [sequence-resolver]} organism text]
  (let [dna (-> text
                (clojure.string/replace #"\R" "")
                (clojure.string/upper-case))
        pretty-dna (if (> (count dna) 40)
                     (format "%s...%s" (subs dna 0 20) (subs dna (- (count dna) 20)))
                     dna)]
    (if (> (count dna) 10)
      (f/attempt-all [coords (resolver/resolve-sequence sequence-resolver organism dna)]
        (convert-coords dna coords))
      (f/fail "Input DNA sequence too short."))))

(defn- parse-grnas
  [sequence-resolver organism text]
  (let [valid-grna? #(and (dna-seq? %)
                         (<= (count %) 23)
                         (>= (count %) 20))
        grnas (->> (clojure.string/split-lines text)
                   (map clojure.string/upper-case))]
    (if (every? valid-grna? grnas)
      (->> (map #(resolver/resolve-sequence sequence-resolver organism %) grnas)
           (map #(if (f/ok? %2) (convert-coords %1 %2) {:error %2 :grna %1}) grnas))
      (f/fail "Input gRNAs must be between 20-23 nt and consist of letters: \"ATCGN\"."))))

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
  [resolver organism req]
  (f/if-let-ok? [query-text (parse-req-string :query-text req)]
    (if (dna-seq? query-text)
        (parse-dna-sequence resolver organism query-text)
        (parse-raw-text query-text (partial parse-line resolver organism) false))))

(defmethod parse-genomic-regions :text-file
  [resolver organism req]
  (let [text (slurp (get-in req [:params :query-file-upload :tempfile]))]
    (parse-raw-text text (partial parse-line resolver organism) false)))

(defmethod parse-genomic-regions :bed-file
  [resolver organism req]
  (let [text (slurp (get-in req [:params :query-file-upload :tempfile]))]
    (parse-raw-text text (partial parse-bed-line resolver organism))))

(defmethod parse-genomic-regions :gtf-file
  [resolver organism req]
  (let [text (slurp (get-in req [:params :query-file-upload :tempfile]))]
    (parse-raw-text text (partial parse-gtf-line resolver organism))))

(defmethod parse-genomic-regions :unknown-file
  [_ _ _]
  (f/fail "Unknown file type."))

(defmethod parse-genomic-regions :fasta-file
  [_ _ req]
  (let [text (slurp (get-in req [:params :query-file-upload :tempfile]))]
    (f/fail "Unsupported file type .fasta")))

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

(defmulti parse-request
  "Parses a standard raw request object into a format suitable for
   processing or returns a Failure object along with an error message."
  (fn [type resolvers req] type))

(defmethod parse-request :standard
  [_ resolvers req]
  "A standard request is one that finds all gRNAs in a list of genomic
  regions constrained by certain user defined options.

  On success, the returned map has the following structure:
     {:genomic-regions [{:region-name X1 :coords [chrX1 start-1 end-1]}
                        {:region-name X2 :coords [chrX2 start-2 end-2]} ...]
      :enzyme STRING
      :organism STRING
      :filter-annotated BOOL          OPTIONAL
      :topn INT                       OPTIONAL
      :flanking INT                   OPTIONAL}"
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
    genomic-regions (parse-genomic-regions resolvers (:organism parsed-request) req)]
   (assoc parsed-request :genomic-regions genomic-regions)))

(defmethod parse-request :grna
  [_ {:keys [sequence-resolver]} req]
  "A gRNA request is one that evaluates a set of gRNAs against the
   Guidescan databases.

   On success, the returned map has the following structure:
     {:grnas [{:seq X1 :coords [chrX1 start-1 end-1]}
              {:seq X2 :coords [chrX2 start-2 end-2]} ...]
      :enzyme STRING
      :organism STRING}"
  (f/attempt-all
   [parsed-request
    (failure-vector-into-map
     [[:enzyme (parse-req-string :enzyme req) :required]
      [:organism (parse-req-string :organism req) :required]
      [:query-text (parse-req-string :query-text req) :required]])
    genomic-regions (parse-grnas sequence-resolver
                                 (:organism parsed-request)
                                 (:query-text parsed-request))]
   (assoc parsed-request :genomic-regions genomic-regions)))

(defmethod parse-request :library
  [_ _ req]
  "A library design requests is one that designs a library according 
  to some user specified parameters for a set of genes.
  
  Simply parses the parameters. Does not resolve gene names until
  later."
  (failure-vector-into-map
   [[:organism (parse-req-string :organism req) :required]
    [:query-text (parse-req-string :query-text req) :required]
    [:num-pools (parse-req-int-range 1 36 :num-pools req) :optional]
    [:prime5-g (parse-req-bool :prime5-g req) :optional]
    [:saturation (parse-req-int-range 1 20 :saturation req) :optional] ; # of gRNA per gene
    [:num-essential (parse-req-decimal-range 0 1 :num-essential req) :optional] 
    [:num-control (parse-req-decimal-range 0 1 :num-control req) :optional]])) 
   
