(ns script-utils
  (:require [clojure.data.csv :as csv]))
  
(defmacro if-let*
  "Multiple binding version of if-let"
  ([bindings then]
   `(if-let* ~bindings ~then nil))
  ([bindings then else]
   (assert (vector? bindings) "a vector for its binding")
   (assert (even? (count bindings)) "exactly even forms in binding vector")
   (let [if-let-else (keyword (name (gensym "if_let_else__")))
         inner (fn inner [bindings]
                 (if (seq bindings)
                   `(if-let [~(first bindings) ~(second bindings)]
                      ~(inner (drop 2 bindings))
                      ~if-let-else)
                   then))]
     `(let [temp# ~(inner bindings)]
        (if (= temp# ~if-let-else) ~else temp#)))))

(defn lazy-lines-gzip
  [gzip-file]
  (->> (java.io.FileInputStream. gzip-file)
       (java.util.zip.GZIPInputStream.)
       (java.io.InputStreamReader.)
       (java.io.BufferedReader.)
       (line-seq)))

(defn read-csv-with-header
  [fname]
  (with-open [f (clojure.java.io/reader fname)]
    (let [rows (csv/read-csv f)
            header (first rows)]
        (-> (map #(zipmap header %) (rest rows))
            (doall)))))
   
