(ns guidescan-web.bam.db
  (:require
   [guidescan-web.config :as config]
   [clojure.java.io :as io]))

(defn- load-bam-reader
  [file]
  (-> (htsjdk.samtools.SamReaderFactory/makeDefault)
    (. open file)))

(defn query-bam
  [config organism chromosone start-pos end-pos]
  (let [grna-db (config/get-grna-db-path config organism)]
    (with-open [bam-reader (load-bam-reader
                            (io/file grna-db))
                iterator (.query bam-reader chromosone start-pos end-pos
                                 false)]
      (doall (iterator-seq iterator)))))

;; Should output 321
;; (str (first (query-bam "ce11" "chrIV" 911770 916325)))
