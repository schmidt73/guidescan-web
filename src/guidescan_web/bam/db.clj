(ns guidescan-web.bam.db
  "This namespace exposes a database component and an interface that
  allows for queries to an underlying guideRNA BAM database generated
  from Guidescan.

  The words \"genome\" and \"organism\" are used essentially
  interchangeably throughout the code."
  (:require
   [guidescan-web.config :as config]
   [clojure.java.io :as io]))

(defn- get-offtarget-delim
  "Gets the delimiter used for parsing off-target info."
  [genome]
  (+ 1 (reduce #(+ %1 (second %2)) 0 genome)))
  
(defn- to-genomic-coordinates
  "Converts from absolute coordinates to
  coordinates with respect to one chromosone."
  [genome absolute-coords]
  (let* [abs-genome (reductions #(+ %1 (second %2)) 0 genome)
         idxs (keep-indexed #(when (> %2 absolute-coords) %1) abs-genome)
         idx (- (first idxs) 1)]
    {:position (- absolute-coords (nth abs-genome idx))
     :chromosone (first (nth genome idx))}))

(defn- to-big-endian-hex-array
  "Takes a byte array in little endian order and converts it
  to a hex array in big endian order."
  [b-array]
  (-> (map #(vector (bit-and 0x0F %) (bit-shift-right (bit-and 0xF0 %) 4))
           b-array)
      (flatten)
      (reverse)))

(defn- twos-complement
  "Returns the 2's complement of a hex array."
  [hex-array]
  (map #(- 15 %) hex-array))

(defn- hex-array-to-int
  "Converts a hex array to an integer, assuming
  big-endian ordering."
  [hex-array]
  (if (>= (first hex-array) 8)
      (- (- (hex-array-to-int (twos-complement hex-array))) 1) 
      (reduce #(+ (* 16 %1) %2) hex-array)))

(defn- convert-offtarget-entry
  [genome entry]
  {:distance (last entry)
   :coords (vec (map #(to-genomic-coordinates genome %)
                     (drop-last (rest entry))))})

(defn- parse-offtarget-info [genome byte-array]
  "Parses the off target info out of a byte array."
  (let [delim (get-offtarget-delim genome)]
    (->> (to-big-endian-hex-array byte-array)
         (partition 16)
         (map hex-array-to-int)
         (map #(Math/abs %))
         (reverse)
         (partition-by #(= delim %))
         (filter #(not= (list delim) %))
         (map #(convert-offtarget-entry genome %)))))

(defn- parse-query-result
  [config organism bam-record]
  (merge {:sequence (.getReadString bam-record)
          :start (.getAlignmentStart bam-record)
          :end (.getAlignmentEnd bam-record)
          :direction (if (.getReadNegativeStrandFlag bam-record) :negative :positive)
          :specificity (.getAttribute bam-record "cs")
          :cutting-efficiency (.getAttribute bam-record "ds")}
         (when-let [barray (.getAttribute bam-record "of")]
           (let [genome (config/get-genome-structure config organism)]
             {:off-targets (parse-offtarget-info genome barray)}))))

(defn- load-bam-reader
  [file]
  (-> (htsjdk.samtools.SamReaderFactory/makeDefault)
    (. open file)))

(defn query-bam-grna-db
  "Queries the BAM gRNA database, and parses the output into
  a sequence of gRNA maps that overlap with the query.

  Can throw an IllegalArgumentException if the chromosone
  is not in the index."
  [config organism chromosone start-pos end-pos]
  (let [grna-db (config/get-grna-db-path config organism)]
    (with-open [bam-reader (load-bam-reader
                            (io/file grna-db))
                iterator (.query bam-reader chromosone start-pos end-pos
                                 false)]
      (let [bam-records (doall (iterator-seq iterator))]
        (vec (map #(parse-query-result config organism %) bam-records))))))
