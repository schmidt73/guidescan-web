(ns script-utils)
  
(defn lazy-lines-gzip
  [gzip-file]
  (->> (java.io.FileInputStream. gzip-file)
       (java.util.zip.GZIPInputStream.)
       (java.io.InputStreamReader.)
       (java.io.BufferedReader.)
       (line-seq)))
