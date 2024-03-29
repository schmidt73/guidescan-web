(ns guidescan-web.genomics.grna
  "Defines the structure of a guideRNAs used througout the
  application. Along with additional utility functions for them."
  (:require [clojure.spec.alpha :as s]))

(s/def ::sequence string?)
(s/def ::start int?)
(s/def ::end int?)
(s/def ::direction #{:positive :negative})
(s/def ::cutting-efficiency float?)
(s/def ::specificity float?)
(s/def ::distance int?)
(s/def ::annotations (s/* string?))
(s/def ::position int?)
(s/def ::chromosome string?)
(s/def ::coord (s/keys :req-un [::position ::chromosome]))
(s/def ::coords (s/+ ::coord))
(s/def ::off-target (s/keys :req-un [::distance ::coords]))
(s/def ::off-targets (s/* ::off-target))

(s/def ::grna
  (s/keys
   :req-un [::sequence ::start ::end ::direction
            ::annotations]
   :opt-un [::off-targets ::specificity ::cutting-efficiency]))
          
(defn num-off-targets
  "Returns the number of off-targets, optionally only considering
  ones at a fixed distance k."
  ([grna]
   (reduce #(+ %1 (if (:coords %2) (count (:coords %2)) 1)) 0 (:off-targets grna)))
  ([grna k]
   (reduce #(+ %1 (if (= k (:distance %2)) (if (:coords %2) (count (:coords %2)) 1) 0))
           0 (:off-targets grna))))

(s/fdef num-off-targets
  :args (s/cat :grna ::grna)
  :ret int?)
