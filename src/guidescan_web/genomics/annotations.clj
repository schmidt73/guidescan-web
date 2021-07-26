(ns guidescan-web.genomics.annotations
  "Defines a component that efficiently finds all overlapping gene
  annotations for a given genomic interval."
  (:import htsjdk.tribble.index.interval.IntervalTree
           htsjdk.tribble.index.interval.Interval)
  (:require [com.stuartsierra.component :as component]
            [failjure.core :as f]
            [guidescan-web.db-pool :as db-pool]
            [honeysql.core :as sql]
            [next.jdbc :as jdbc]
            [taoensso.timbre :as timbre]))

(defn private-field 
  "A dirty dirty dirty hack that I regret putting into this codebase.
  It is the only choice though until I decide to write my own interval
  tree implementation."
  [obj fn-name-string]
  (let [m (.. obj getClass (getDeclaredField fn-name-string))]
    (. m (setAccessible true))
    (. m (get obj))))

(defn- create-interval-tree
  [annotations]
  (let [it (new IntervalTree)]
    (doseq [{:keys [:exons/start_pos :exons/end_pos]} annotations]
       (.insert it (new Interval start_pos end_pos)))
    it))

(defn- create-interval-map
  [annotations]
  (->> annotations
       (map (fn [{:keys [:exons/start_pos :exons/end_pos] :as annot}]
                [[start_pos end_pos] annot]))
       (into {})))

(defn- get-annotations-helper
  [it-tree it-map start end]
  (->> (.findOverlapping it-tree (new Interval start end))
    (map #(vector (private-field % "start") (private-field % "end")))
    (mapv #(get it-map %))))

(defn- map-in [m f]
  (into {} (for [[k v] m] [k (f v)])))

(defn load-annotations
  [db-pool]
  (try
    (with-open [conn (db-pool/get-db-conn db-pool)]
      (let [annotations (jdbc/execute! conn (sql/format (sql/build :select :* :from [:exons])))]
        (->> annotations 
             (group-by :exons/chromosome))))
    (catch java.sql.SQLException e
      (timbre/error "Cannot acquire DB connection to load annotations.")
      nil)))

(defrecord GeneAnnotations [interval-trees interval-maps db-pool]
  component/Lifecycle
  (start [this]
    (timbre/info "Constructing interval trees for gene annotations.")
    (if-let [annotations (load-annotations db-pool)]
      (assoc this :interval-maps (map-in annotations create-interval-map)
                  :interval-trees (map-in annotations create-interval-tree))))
  (stop [this]))

(defn gene-annotations []
  (map->GeneAnnotations {}))

(defn get-annotations
  "Returns a list of annotations that overlap with
  the input region."
  [gene-annotations accession start end]
  (let [it-map (get (:interval-maps gene-annotations) accession)
        it-tree (get (:interval-trees gene-annotations) accession)]
    (if (and it-map it-tree)
      (get-annotations-helper it-tree it-map start end)
      [])))  

;; I need a macro that allows me to combine let and conditionals
;; in a more flexible way.
