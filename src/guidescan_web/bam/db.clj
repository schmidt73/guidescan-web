(ns guidescan-web.bam.db
  (:require
   [clojure.java.io :as io]))

(def ce11-bam-file
  (str "/home/schmidt73/Desktop/guidescan/guidescan-website"
       "/database/cas9_ce11_all_guides.bam"))

(def organism-file-map
  {"ce11" ce11-bam-file})

(defn- load-bam-reader
  [file]
  (-> (htsjdk.samtools.SamReaderFactory/makeDefault)
    (. open file)))

(defn query-bam
  [organism chromosone start-pos end-pos]
  (with-open [bam-reader (load-bam-reader (io/file (get organism-file-map organism)))
              iterator (.query bam-reader chromosone start-pos end-pos
                               false)]
    (doall (iterator-seq iterator))))

;; Should output 321
(count (query-bam "ce11" "chrIV" 911770 916325))
