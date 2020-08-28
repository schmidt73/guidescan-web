(ns guidescan-web.query.jobs-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [guidescan-web.mock :as mock]
            [guidescan-web.query.jobs :as jobs]))

(def test-params-good
  {:params {:query-text "chrV:91177-916325"
            :organism "ce11" :enzyme "cas9"
            :mode "within" :flank-size 1000
            :ordering "offtargets" :display "all"
            :top-n-results 3}})

(def test-params-bad-chromosone
  {:params {:query-text "chr1:91177-916325"
            :organism "ce11" :enzyme "cas9"
            :mode "within" :flank-size 1000
            :ordering "offtargets" :display "all"
            :top-n-results 3}})

(def test-params-bad-parse-1
  {:params {:query-text "chr1:91177916325"
            :organism "ce11" :enzyme "cas9"
            :mode "within" :flank-size 1000
            :ordering "offtargets" :display "all"
            :top-n-results 3}})

(def test-params-bad-parse-2
  {:params {:query-text "chr1:91177-91a325"
            :organism "ce11" :enzyme "cas9"
            :mode "within" :flank-size 1000
            :ordering "offtargets" :display "all"
            :top-n-results 3}})

(deftest successful-job-queue-submission
  (testing "Testing job queue submission routine for success."
    (mock/with-component-or-system system (mock/test-system-no-www)
      (let [job-queue (:job-queue system)
            job-id (jobs/submit-query job-queue test-params-good)]
        (is (nil? (:error (jobs/get-job job-queue job-id))))))))

(deftest bad-job-queue-submission-chromosone
  (testing "Testing job queue submission routine for error because of
           invalid chromosone name."
    (mock/with-component-or-system system (mock/test-system-no-www)
      (let [job-queue (:job-queue system)
            job-id (jobs/submit-query job-queue test-params-bad-chromosone)]
        (is (some? (:error (jobs/get-job job-queue job-id))))))))

(deftest bad-job-queue-submission-parsing-1
  (testing "Testing job queue submission routine for error because of
           failed query parsing."
    (mock/with-component-or-system system (mock/test-system-no-www)
      (let [job-queue (:job-queue system)
            job-id (jobs/submit-query job-queue test-params-bad-parse-1)]
        (is (some? (:error (jobs/get-job job-queue job-id))))))))

(deftest bad-job-queue-submission-parsing-2
  (testing "Testing job queue submission routine for error because of
           failed query parsing."
    (mock/with-component-or-system system (mock/test-system-no-www)
      (let [job-queue (:job-queue system)
            job-id (jobs/submit-query job-queue test-params-bad-parse-2)]
        (is (some? (:error (jobs/get-job job-queue job-id))))))))
