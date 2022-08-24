(ns guidescan-web.bam.db
  "This namespace exposes a database component and an interface that
  allows for queries to an underlying guideRNA BAM database generated
  from Guidescan.

  The words \"genome\" and \"organism\" are used essentially
  interchangeably throughout the code."
  (:require
   [clojure.spec.alpha :as s]
   [clojure.java.io :as io]
   [failjure.core :as f]
   [com.stuartsierra.component :as component]
   [taoensso.timbre :as timbre]
   [guidescan-web.config :as config]
   [guidescan-web.genomics.resolver :as resolver]
   [guidescan-web.genomics.grna :as grna]
   [guidescan-web.genomics.structure :as genome-structure]))

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
   :coords (vec (map #(genome-structure/to-genomic-coordinates genome %)
                     (drop-last (rest entry))))})

(defn- parse-offtarget-info [genome-structure byte-array]
  "Parses the off target info out of a byte array."
  (let [delim (:off-target-delim genome-structure)]
    (->> (to-big-endian-hex-array byte-array)
         (partition 16)
         (map hex-array-to-int)
         (reverse)
         (partition-by #(= delim %))
         (filter #(not= (list delim) %))
         (map #(convert-offtarget-entry genome-structure %))
         (map (fn [{:keys [distance coords]}]
                (map #(assoc % :distance distance) coords))) 
         (flatten))))

(defn- resolve-accession-names [gene-resolver organism off-targets]
  (map
   #(let [accession (:chromosome %)
          chr (resolver/resolve-chromosome-accession gene-resolver organism accession)]
     (assoc % :accession accession :chromosome chr))
   off-targets))
                               
(defn- parse-query-result
  [genome-structure gene-resolver organism bam-record]
  (merge {:sequence (.getReadString bam-record)
          :start (.getAlignmentStart bam-record)
          :end (.getAlignmentEnd bam-record)
          :direction (if (.getReadNegativeStrandFlag bam-record) :negative :positive)}
         (when-let [cutting-efficiency (.getAttribute bam-record "ds")]
           {:cutting-efficiency cutting-efficiency})
         (when-let [specificity (.getAttribute bam-record "cs")]
           {:specificity specificity})
         (when-let [barray (.getAttribute bam-record "of")]
           {:off-targets (->> barray
                              (parse-offtarget-info genome-structure)
                              (resolve-accession-names gene-resolver organism))})
         (when-let [d0 (.getAttribute bam-record "k0")]
           {:distance-0-off-targets d0})
         (when-let [d1 (.getAttribute bam-record "k1")]
           {:distance-1-off-targets d1})
         (when-let [d2 (.getAttribute bam-record "k2")]
           {:distance-2-off-targets d2})
         (when-let [d3 (.getAttribute bam-record "k3")]
           {:distance-3-off-targets d3})
         (when-let [d4 (.getAttribute bam-record "k4")]
           {:distance-4-off-targets d4})))

(defn- load-bam-reader
  [file]
  (-> (htsjdk.samtools.SamReaderFactory/makeDefault)
      (.validationStringency htsjdk.samtools.ValidationStringency/SILENT)
      (.open file)))

(defn get-genome-structure-map
  "Parses the genome structure out of all the BAM file headers into a
  map from organism-enzyme pairs to genome structures. The
  genome-structure is necessary to resolve the absolute coordinates
  stored in the database to relative coordinates."
  [config]
  (into {}
   (for [{organism :organism enzyme :enzyme} (keys (:grna-database-path-map (:config config)))]
     (let [grna-db (config/get-grna-db-path config organism enzyme)]
       (with-open [bam-reader (load-bam-reader (io/file grna-db))]
         (as-> (.getFileHeader bam-reader) e
           (.getSequenceDictionary e)
           (.getSequences e)
           (map #(vector (.getSequenceName %) (.getSequenceLength %)) e)
           (genome-structure/get-genome-structure e)
           (vector {:organism organism :enzyme enzyme} e)))))))

(defrecord BamDB [config genome-structure-map gene-resolver]
  component/Lifecycle
  (start [this]
    (timbre/info "Loading genome structure map.")
    (when (nil? genome-structure-map)
      (assoc this :genome-structure-map (get-genome-structure-map config))))
  (stop [this]))
              
(defn create-bam-db []
  (map->BamDB {}))

(defn query-bam-grna-db
  "Queries the BAM gRNA database, and parses the output into
  a sequence of gRNAs that overlap with the query.

  Will return a failure object if the chromosome is not in the index
  or if the organism-enzyme pair is not supported."
  [bam-db organism enzyme chromosome start-pos end-pos]
  (f/attempt-all
   [_ (or (config/contains-grna-db-path? (:config bam-db) organism enzyme)
          (f/fail (format "Unsupported organism-enzyme pair: %s-%s" organism enzyme)))
    grna-db (config/get-grna-db-path (:config bam-db) organism enzyme)
    genome-structure (get (:genome-structure-map bam-db) {:organism organism :enzyme enzyme})
    gene-resolver (:gene-resolver bam-db)]
   (try
     (with-open [bam-reader (load-bam-reader
                             (io/file grna-db))
                 iterator (.query bam-reader chromosome start-pos end-pos
                                  false)]
       (let [bam-records (doall (iterator-seq iterator))]
         (vec (map #(parse-query-result genome-structure gene-resolver organism %) bam-records))))
     (catch java.lang.IllegalArgumentException _
       (f/fail (str "Invalid chromosome \"" chromosome "\" for organism."))))))
