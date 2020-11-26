(ns guidescan-web.genomics.annotations
  "Defines a component that efficiently finds all overlapping gene
  annotations for a given genomic interval."
  (:import htsjdk.tribble.index.interval.IntervalTree
           htsjdk.tribble.index.interval.Interval)
  (:require [com.stuartsierra.component :as component]
            [failjure.core :as f]
            [taoensso.timbre :as timbre]))

(defn private-field 
  "A dirty dirty dirty hack that I regret putting into this codebase.
  It is the only choice though until I decide to write my own interval
  tree implementation."
  [obj fn-name-string]
  (let [m (.. obj getClass (getDeclaredField fn-name-string))]
    (. m (setAccessible true))
    (. m (get obj))))

(defn- parse-annotations-file
  [annotations-file]
  (->> (slurp annotations-file)
    (clojure.string/split-lines)
    (map #(clojure.string/split % #"\t"))
    (map (fn [[chr start end annot]]
             [chr (Integer/parseInt start) (Integer/parseInt end) annot]))))

(defn- create-interval-tree
  [annotations]
  (let [it (new IntervalTree)]
    (doseq [[_ start end _ :as annotation] annotations]
       (.insert it (new Interval start end)))
    it))

(defn- create-interval-map
  [annotations]
  (into {} (map (fn [[chr start end annot]] [[start end] [chr annot]])
                annotations)))

(defn- get-annotations-helper
  [it-tree it-map chr start end]
  (->> (.findOverlapping it-tree (new Interval start end))
    (map #(vector (private-field % "start") (private-field % "end")))
    (map #(into (get it-map %) %))
    (filterv #(= (first %) chr))))

(defn- map-in [m f]
  (into {} (for [[k v] m] [k (f v)])))

(defrecord GeneAnnotations [interval-trees interval-maps config]
  component/Lifecycle
  (start [this]
    (timbre/info "Constructing interval trees for gene annotations.")
    (when (or (nil? interval-trees) (nil? interval-maps))
      (if-let [annotations-map (get-in config [:config :annotations-map])]
        (let [annotations (map-in annotations-map parse-annotations-file)]
          (if (empty? annotations)
            (do
              (timbre/warn ":annotations-map is empty, continuing regardless.")
              this)
            (assoc this :interval-maps (map-in annotations create-interval-map)
                        :interval-trees (map-in annotations create-interval-tree))))
        (do
          (timbre/warn ":annotations-map not found, continuing regardless.")
          this))))
  (stop [this]))

(defn gene-annotations []
  (map->GeneAnnotations {}))

(defn get-annotations
  "Returns a list of annotations that overlap with
  the input region."
  [gene-annotations organism chr start end]
  (let [it-map (get (:interval-maps gene-annotations) organism)
        it-tree (get (:interval-trees gene-annotations) organism)]
    (if (and it-map it-tree)
      (get-annotations-helper it-tree it-map chr start end)
      [])))  

;; I need a macro that allows me to combine let and conditionals
;; in a more flexible way.
