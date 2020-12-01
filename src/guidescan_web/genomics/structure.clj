(ns guidescan-web.genomics.structure
  "A genome structure map specificies the names and lengths of all the
  chromosomes for a given organism. It is necessary for mapping
  between absolute and relative coordinates.

  A raw genome structure looks like this:
     [[chr1 chr1-length] ... [chrN chrN-length]]")

(defn- find-position
  "Finds the index of the value in an ascending sorted array using
  binary search, if the value is not found, it returns the index of
  the rightmost element whose value is less than the search value."
  ([arr v sp ep]
   (if (>= sp ep) ep
     (let [idx (+ sp (long (Math/ceil (/ (- ep sp) 2))))
           middle-val (nth arr idx)]
       (cond
         (<= middle-val v) (find-position arr v idx ep)
         :otherwise        (find-position arr v 0 (- idx 1))))))
  ([arr v]
   (find-position arr v 0 (- (count arr) 1))))
 
(defn to-genomic-coordinates
  "Converts from absolute coordinates to
  coordinates with respect to one chromosone."
  [genome-structure absolute-coords]
  (let [direction (if (> absolute-coords 0) :positive :negative) 
        absolute-coords (Math/abs absolute-coords)
        abs-genome (:absolute-genome genome-structure)
        genome (:genome genome-structure)
        idx (find-position abs-genome absolute-coords)]
    {:position (- absolute-coords (nth abs-genome idx))
     :chromosome (first (nth genome idx))
     :direction direction}))

(defn- get-absolute-genome-structure
  [genome-structure]
  (vec (reductions #(+ %1 (second %2)) 0 genome-structure))) 

(defn- get-offtarget-delim
  "Gets the delimiter used for parsing off-target info."
  [genome-structure]
  (- (+ 1 (reduce #(+ %1 (second %2)) 0 genome-structure))))
 
(defn get-genome-structure
  "Converts raw genome-structure vector into a form suitable for efficient
  search.

  raw-genome-structure has form:
     [[chr1 chr1-length] ... [chrN chrN-length]]"
  [raw-genome-structure]
  (let [absolute-genome (get-absolute-genome-structure raw-genome-structure)
        off-target-delim (get-offtarget-delim raw-genome-structure)]
    {:genome raw-genome-structure
     :absolute-genome absolute-genome
     :off-target-delim off-target-delim}))
