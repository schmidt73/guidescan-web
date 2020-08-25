(ns guidescan-web.query.parsing
  "This namespace exposes the method for parsing user queries into a
  format suitable for processing.

  It assumes the spec located here for BED files:
    - https://m.ensembl.org/info/website/upload/bed.html
  It assumes the spec located here for GFF/GTF files:
    - https://uswest.ensembl.org/io/website/upload/gff.html")

(defn failure?
  [parse-tree]
  (contains? parse-tree :failure))

(defn success?
  [parse-tree]
  (contains? parse-tree :success))

(defn skip?
  [parse-tree]
  (= :skip (:success parse-tree)))

(defn- parse-line
  "Parses one line of a text file, returning a parse tree indicating
  success or failure along with an error message."
  [line-number line]
  (if-let [[_ chr start-str end-str] (re-find #"^(chr.*):(\d+)-(\d+)" line)]
    {:success
      [chr (Integer/parseInt start-str) (Integer/parseInt end-str)]}
    {:failure (str "Failed to parse: \"" line "\" on line " (+ 1 line-number))}))

(defn- parse-gtf-line
  "Parses one line of a .gtf file, returning a parse tree indicating
  success or failure along with an error message."
  [line-number line]
  (if-let [[_ chr start-str end-str] (re-find #"^(.+)\t.+\t.+\t(\d+)\t(\d+)\t.+\t.+\t.+\t.*" line)]
    {:success
     [chr (- (Integer/parseInt start-str) 1) (Integer/parseInt end-str)]}
    (if (re-find #"(?i)track(\s|$).*" line)
      {:success :skip}
      {:failure (str "Invalid GTF row: \"" line "\" on line " (+ 1 line-number))})))

(defn- parse-bed-line
  "Parses one line of .bed file, returning a parse tree indicating
  success or failure along with an error message."
  [line-number line]
  (if-let [[_ chr start-str end-str] (re-find #"^(\S+)\s+(\d+)\s+(\d+)(\s|$).*" line)]
    {:success
      [chr (Integer/parseInt start-str) (Integer/parseInt end-str)]}
    (if (re-find #"(?i)(track|browser)(\s|$).*" line)
      {:success :skip}
      {:failure (str "Failed to parse: \"" line "\" on line " (+ 1 line-number))})))

(defn- parse-raw-text
  "Parses the raw text line by line using the passed in
  line-parser. Returns the first error if any exists."
  [text line-parser]
  (let* [parsed-lines
         (->> (clojure.string/split-lines text)
              (map-indexed line-parser)
              (remove skip?)) 
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
  "Parses the parameters of a query into a 0 based half-open coordinate
  format. The multimethod dispatches on the type of query, and it
  handles raw text input, BED files, GTF files, and FASTA files.

  The method returns a map indicating either a successful parse tree
  or failure to parse along with an error message.

  A succesful parse tree looks like this:
  [[chrX1 start-1 end-1] [chrX2 start-2 end-2] ...]"
   get-query-type)

(defmethod parse-query :text
  [req-params]
  (parse-raw-text (:query-text req-params) parse-line))

(defmethod parse-query :text-file
  [req-params]
  (let [text (slurp (get-in req-params [:query-file-upload :tempfile]))]
    (parse-raw-text text parse-line)))

(defmethod parse-query :bed-file
  [req-params]
  (let [text (slurp (get-in req-params [:query-file-upload :tempfile]))]
    (parse-raw-text text parse-bed-line)))

(defmethod parse-query :gtf-file
  [req-params]
  (let [text (slurp (get-in req-params [:query-file-upload :tempfile]))]
    (parse-raw-text text parse-gtf-line)))

(defmethod parse-query :unknown-file
  [_]
  {:failure "Unknown file type."})

(defmethod parse-query :fasta-file
  [_]
  {:failure "Unsupported file type .fasta"})
