(ns guidescan-web.query.parsing-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [guidescan-web.mock :as mock]
            [failjure.core :as f]
            [guidescan-web.query.parsing :as query-parsing]))

(def good-text-query
  {:params {:query-text "chrIV:911770-916325\nchrIV:911100-911200" :organism "ce11"}})

(def bad-text-query
  {:params {:query-text "chIV:911770-916325\nchrIV:911100-911200" :organism "ce11"}})

(def good-text-file-query
  {:params {:query-file-upload {:filename "query_good_test.txt"
                                :tempfile (io/resource "query_good_test.txt")}}})

(def bad-text-file-query
  {:params {:query-file-upload {:filename "query_bad_test.txt"
                                :tempfile (io/resource "query_bad_test.txt")}}})

(def good-bed-file-query
  {:params {:query-file-upload {:filename "query_good_test.bed"
                                :tempfile (io/resource "query_good_test.bed")}}})

(def bad-bed-file-query
  {:params {:query-file-upload {:filename "query_bad_test.bed"
                                :tempfile (io/resource "query_bad_test.bed")}}})

(def good-gtf-file-query
  {:params {:query-file-upload {:filename "query_good_test.gtf"
                                :tempfile (io/resource "query_good_test.gtf")}}})

(def bad-gtf-file-query
  {:params {:query-file-upload {:filename "query_bad_test.gtf"
                                :tempfile (io/resource "query_bad_test.gtf")}}})

(deftest successful-text-query
  (testing "Testing query parser for good text query."
    (clojure.test/is
     (mock/with-component-or-system system (mock/test-system-no-www)
       (f/ok?
        (query-parsing/parse-genomic-regions (:gene-resolver system)
                                             good-text-query))))))

(deftest failing-text-query
  (testing "Testing query parser for bad text query."
    (clojure.test/is
     (mock/with-component-or-system system (mock/test-system-no-www)
       (f/failed?
        (query-parsing/parse-genomic-regions (:gene-resolver system)
                                             bad-text-query))))))

(deftest successful-text-file-query
  (testing "Testing query parser for a good text file query."
    (clojure.test/is
     (mock/with-component-or-system system (mock/test-system-no-www)
       (f/ok?
        (query-parsing/parse-genomic-regions (:gene-resolver system)
                                             good-text-file-query))))))

(deftest failing-text-file-query
  (testing "Testing query parser for bad text file query."
    (clojure.test/is
     (mock/with-component-or-system system (mock/test-system-no-www)
       (f/failed?
        (query-parsing/parse-genomic-regions (:gene-resolver system)
                                             bad-text-file-query))))))

(deftest successful-bed-file-query
  (testing "Testing query parser for a good bed file query."
    (clojure.test/is
     (mock/with-component-or-system system (mock/test-system-no-www)
       (f/ok?
        (query-parsing/parse-genomic-regions (:gene-resolver system)
                                             good-bed-file-query))))))

(deftest failing-bed-file-query
  (testing "Testing query parser for bad bed file query."
    (clojure.test/is
     (mock/with-component-or-system system (mock/test-system-no-www)
       (f/failed?
        (query-parsing/parse-genomic-regions (:gene-resolver system)
                                             bad-bed-file-query))))))

(deftest successful-gtf-file-query
  (testing "Testing query parser for a good gtf file query."
    (clojure.test/is
     (mock/with-component-or-system system (mock/test-system-no-www)
       (f/ok?
        (query-parsing/parse-genomic-regions (:gene-resolver system)
                                             good-gtf-file-query))))))

(deftest failing-gtf-file-query
  (testing "Testing query parser for bad gtf file query."
    (clojure.test/is
     (mock/with-component-or-system system (mock/test-system-no-www)
       (f/failed?
        (query-parsing/parse-genomic-regions (:gene-resolver system)
                                             bad-gtf-file-query))))))

(def successful-full-req
  {:params {:query-text "chrIV:1100-45000\nchrIV:1100-45000"
            :enzyme "cas9"
            :organism "ce11"
            :filter-annotated "false"
            :topn "17"
            :flanking "45"}})

(def successful-req-bad-topn
  {:params {:query-text "chrIV:1100-45000\nchrIV:1100-45000"
            :enzyme "cas9"
            :organism "ce11"
            :filter-annotated "false"
            :topn "a17"
            :flanking "45"}})

(def successful-req-bad-topn-flanking
  {:params {:query-text "chrIV:1100-45000\nchrIV:1100-45000"
            :enzyme "cas9"
            :organism "ce11"
            :filter-annotated "false"
            :topn "a17"
            :flanking "4aa5"}})

(def failed-req-bad-query
  {:params {:query-text "chrIV:11a00-450-00\nchrIV:1100-45000"
            :enzyme "cas9"
            :organism "ce11"
            :filter-annotated "false"
            :topn "a17"
            :flanking "4aa5"}})

(deftest successful-full-req-test
  (testing "Testing request parsing for a full set of parameters"
    (clojure.test/is
     (mock/with-component-or-system system (mock/test-system-no-www)
       (= (query-parsing/parse-request (:gene-resolver system) successful-full-req)
          {:genomic-regions [{:region-name "chrIV:1100-45000" :coords ["chrIV" 1100 45000]}
                             {:region-name "chrIV:1100-45000" :coords ["chrIV" 1100 45000]}]
           :enzyme "cas9",
           :organism "ce11",
           :filter-annotated false,
           :topn 17,
           :flanking 45})))))

(deftest successful-req-bad-topn-test
  (testing "Testing request parsing for a invalid topn string"
    (clojure.test/is
     (mock/with-component-or-system system (mock/test-system-no-www)
       (= (query-parsing/parse-request (:gene-resolver system)
                                       successful-req-bad-topn)
          {:genomic-regions [{:region-name "chrIV:1100-45000" :coords ["chrIV" 1100 45000]}
                             {:region-name "chrIV:1100-45000" :coords ["chrIV" 1100 45000]}]
           :enzyme "cas9"
           :organism "ce11"
           :filter-annotated false
           :flanking 45})))))

(deftest successful-req-bad-topn-flanking-test
  (testing (str "Testing request parsing for a invalid flanking and topn"
                "string")
    (mock/with-component-or-system system (mock/test-system-no-www)
      (clojure.test/is
       (= (query-parsing/parse-request (:gene-resolver system)
                                       successful-req-bad-topn-flanking)
          {:genomic-regions [{:region-name "chrIV:1100-45000" :coords ["chrIV" 1100 45000]}
                             {:region-name "chrIV:1100-45000" :coords ["chrIV" 1100 45000]}]
           :enzyme "cas9"
           :organism "ce11"
           :filter-annotated false})))))

(deftest failed-req-bad-query-test
  (testing "Testing request parsing for a bad query string"
    (mock/with-component-or-system system (mock/test-system-no-www)
     (clojure.test/is
      (.startsWith
       (f/message
        (query-parsing/parse-request (:gene-resolver system)
                                     failed-req-bad-query))
       (str "Failed to parse: \"chrIV:11a00-450-00\" on line 1\n"))))))
