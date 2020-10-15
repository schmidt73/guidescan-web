(ns guidescan-web.query.process-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [failjure.core :as f]
            [guidescan-web.mock :as mock]
            [guidescan-web.genomics.grna :as grna]
            [guidescan-web.query.process :as process]))

(def test-params-good
  {:params {:query-text "chrIV:911700-911800"
            :organism "ce11" :enzyme "cas9"
            :mode "within" :flank-size 1000
            :ordering "offtargets" :display "all"}})

(def test-params-bad-no-organism
  {:params {:query-text "chrIV:911700-911800"
            :organism "hg38" :enzyme "cas9"
            :mode "within" :flank-size 1000
            :ordering "offtargets" :display "all"}})

(def test-params-bad-no-enzyme
  {:params {:query-text "chrIV:911700-911800"
            :organism "ce11" :enzyme "cas10"
            :mode "within" :flank-size 1000
            :ordering "offtargets" :display "all"}})

(deftest successful-query-processing
  (testing "Testing that a query is successfully processed."
    (mock/with-component-or-system system (mock/test-system-no-www)
      (let [[[_ processed-grnas]] (process/process-query (:bam-db system)
                                                         (:gene-annotations system)
                                                         test-params-good)]
        (is (and
             (= 2 (count processed-grnas))
             (= 2 (grna/num-off-targets (nth processed-grnas 0)))
             (= 3 (grna/num-off-targets (nth processed-grnas 1)))
             (= :negative (:direction (nth processed-grnas 0)))
             (= :negative (:direction (nth processed-grnas 1)))))))))

(deftest no-organism-query-processing
  (testing "Testing that a query is successfully processed."
    (mock/with-component-or-system system (mock/test-system-no-www)
      (let [result (process/process-query (:bam-db system)
                                          (:gene-annotations system)
                                          test-params-bad-no-organism)]
        (is (= "Unsupported organism-enzyme pair: hg38-cas9" (f/message result)))))))

(deftest no-enzyme-query-processing
  (testing "Testing that a query is successfully processed."
    (mock/with-component-or-system system (mock/test-system-no-www)
      (let [result (process/process-query (:bam-db system)
                                          (:gene-annotations system)
                                          test-params-bad-no-enzyme)]
        (is (= "Unsupported organism-enzyme pair: ce11-cas10" (f/message result)))))))

(deftest null-query-processing
  (testing "Testing that a query is successfully processed."
    (mock/with-component-or-system system (mock/test-system-no-www)
      (let [result (process/process-query (:bam-db system)
                                          (:gene-annotations system)
                                          {})]
        (is (= "Invalid POST/GET request parameters." (f/message result)))))))
