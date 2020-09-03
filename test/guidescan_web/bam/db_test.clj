(ns guidescan-web.bam.db-test
  (:require [clojure.test :refer :all]
            [guidescan-web.mock :as mock]
            [guidescan-web.bam.db :as db]))

(deftest grna-successful-query-test
  (testing "gRNA query test"
    (is
     (mock/with-component-or-system system (mock/test-system-no-www)
       (let [bam-db (:bam-db system)
             grnas (db/query-bam-grna-db bam-db "ce11" "chrIV" 911770 911820)
             grnas-inside (filter #(not (or (< (:start %) 911770)
                                            (> (:end %) 911820)))
                                  grnas)]
         (and
          (= 2 (count grnas-inside))
          (= (:start (nth grnas-inside 0))) 911784
          (= (:end (nth grnas-inside 0))) 911806
          (= (:direction (nth grnas-inside 0))) :positive
          (= (:start (nth grnas-inside 1))) 911794
          (= (:end (nth grnas-inside 1))) 911816
          (= (:direction (nth grnas-inside 1))) :negative))))))
