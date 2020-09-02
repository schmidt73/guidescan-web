(ns guidescan-web.query.process-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [guidescan-web.mock :as mock]
            [guidescan-web.genomics.grna :as grna]
            [guidescan-web.query.process :as process]))

(def test-params-good
  {:params {:query-text "chrV:911700-911800"
            :organism "ce11" :enzyme "cas9"
            :mode "within" :flank-size 1000
            :ordering "offtargets" :display "all"}})

(deftest successful-query-processing
  (testing "Testing that a query is successfully processed."
    (mock/with-component-or-system config (mock/test-config)
      (let [[[_ processed-grnas]] (process/process-query config test-params-good)]
        (is (and
             (= 6 (count processed-grnas))
             (= 0 (grna/num-off-targets (nth processed-grnas 0)))
             (= 4 (grna/num-off-targets (nth processed-grnas 1)))
             (= 5 (grna/num-off-targets (nth processed-grnas 2)))
             (= 23 (grna/num-off-targets (nth processed-grnas 3)))
             (= 30 (grna/num-off-targets (nth processed-grnas 4)))
             (= 66 (grna/num-off-targets (nth processed-grnas 5)))
             (= :negative (:direction (nth processed-grnas 0)))
             (= :negative (:direction (nth processed-grnas 1)))
             (= :negative (:direction (nth processed-grnas 2)))
             (= :negative (:direction (nth processed-grnas 3)))
             (= :positive (:direction (nth processed-grnas 4)))
             (= :negative (:direction (nth processed-grnas 5)))))))))
