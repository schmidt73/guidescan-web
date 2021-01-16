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
            :mode "within" 
            :ordering "offtargets" :display "all"}})

(def test-params-bad-no-organism
  {:params {:query-text "chrIV:911700-911800"
            :organism "hg38" :enzyme "cas9"
            :mode "within" 
            :ordering "offtargets" :display "all"}})

(def test-params-bad-no-enzyme
  {:params {:query-text "chrIV:911700-911800"
            :organism "ce11" :enzyme "cas10"
            :mode "within"
            :ordering "offtargets" :display "all"}})

(deftest filter-results-test
  (testing "That result filtering works correctly."
    (let [result
          (process/filter-results
           {:filter-annotated false
            :cutting-efficiency-bounds {:upper 1 :lower 0}
            :specificity-bounds {:upper 10 :lower 0}}
           [{:start 150 :end 175 :cutting-efficiency 0.8}]
           {:coords ["chr" 100 200]})]
      (is (= result '({:start 150, :end 175, :cutting-efficiency 0.8}))))))

(deftest successful-query-processing
  (testing "Testing that a query is successfully processed."
    (mock/with-component-or-system system (mock/test-system-no-www)
      (let [{[[_ processed-grnas]] :result}
            (process/process-query system test-params-good)]
        (is (and
             (= 2 (count processed-grnas))
             (= 2 (grna/num-off-targets (nth processed-grnas 0)))
             (= 3 (grna/num-off-targets (nth processed-grnas 1)))
             (= :negative (:direction (nth processed-grnas 0)))
             (= :negative (:direction (nth processed-grnas 1)))))))))

(deftest no-organism-query-processing
  (testing "Testing that a query is successfully processed."
    (mock/with-component-or-system system (mock/test-system-no-www)
      (let [result (process/process-query system
                                          test-params-bad-no-organism)]
        (is (= "Unsupported organism-enzyme pair: hg38-cas9" (f/message result)))))))

(deftest no-enzyme-query-processing
  (testing "Testing that a query is successfully processed."
    (mock/with-component-or-system system (mock/test-system-no-www)
      (let [result (process/process-query system
                                          test-params-bad-no-enzyme)]
        (is (= "Unsupported organism-enzyme pair: ce11-cas10" (f/message result)))))))

(deftest null-query-processing
  (testing "Testing that a query is successfully processed."
    (mock/with-component-or-system system (mock/test-system-no-www)
      (let [result (process/process-query system
                                          {})]
        (is (f/failed? result))))))
