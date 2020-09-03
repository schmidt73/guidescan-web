(ns guidescan-web.query.process-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [guidescan-web.mock :as mock]
            [guidescan-web.genomics.grna :as grna]
            [guidescan-web.query.process :as process]))

(def test-params-good
  {:params {:query-text "chrIV:911700-911800"
            :organism "ce11" :enzyme "cas9"
            :mode "within" :flank-size 1000
            :ordering "offtargets" :display "all"}})

(deftest successful-query-processing
  (testing "Testing that a query is successfully processed."
    (mock/with-component-or-system system (mock/test-system-no-www)
      (let [[[_ processed-grnas]] (process/process-query (:config system)
                                                         (:gene-annotations system)
                                                         test-params-good)]
        (is (and
             (= 2 (count processed-grnas))
             (= 2 (grna/num-off-targets (nth processed-grnas 0)))
             (= 3 (grna/num-off-targets (nth processed-grnas 1)))
             (= :negative (:direction (nth processed-grnas 0)))
             (= :negative (:direction (nth processed-grnas 1)))))))))
