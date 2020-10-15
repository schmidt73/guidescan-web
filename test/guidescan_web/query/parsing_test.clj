(ns guidescan-web.query.parsing-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [failjure.core :as f]
            [guidescan-web.query.parsing :as query-parsing]))

(def good-text-query-params
  {:query-text "chrIV:911770-916325\nchrIV:911100-911200" :organism "ce11"})

(def bad-text-query-params
  {:query-text "chIV:911770-916325\nchrIV:911100-911200" :organism "ce11"})

(def good-text-file-query-params
  {:query-file-upload {:filename "query_good_test.txt"
                       :tempfile (io/resource "query_good_test.txt")}})

(def bad-text-file-query-params
  {:query-file-upload {:filename "query_bad_test.txt"
                       :tempfile (io/resource "query_bad_test.txt")}})

(def good-bed-file-query-params
  {:query-file-upload {:filename "query_good_test.bed"
                       :tempfile (io/resource "query_good_test.bed")}})

(def bad-bed-file-query-params
  {:query-file-upload {:filename "query_bad_test.bed"
                       :tempfile (io/resource "query_bad_test.bed")}})

(def good-gtf-file-query-params
  {:query-file-upload {:filename "query_good_test.gtf"
                       :tempfile (io/resource "query_good_test.gtf")}})

(def bad-gtf-file-query-params
  {:query-file-upload {:filename "query_bad_test.gtf"
                       :tempfile (io/resource "query_bad_test.gtf")}})

(deftest successful-text-query
  (testing "Testing query parser for good text query."
    (clojure.test/is
     (f/ok?
      (query-parsing/parse-query good-text-query-params)))))
  
(deftest failing-text-query
  (testing "Testing query parser for bad text query."
    (clojure.test/is
     (f/failed?
      (query-parsing/parse-query bad-text-query-params)))))

(deftest successful-text-file-query
  (testing "Testing query parser for a good text file query."
    (clojure.test/is
     (f/ok?
      (query-parsing/parse-query good-text-file-query-params)))))
  
(deftest failing-text-file-query
  (testing "Testing query parser for bad text file query."
    (clojure.test/is
     (f/failed?
      (query-parsing/parse-query bad-text-file-query-params)))))

(deftest successful-bed-file-query
  (testing "Testing query parser for a good bed file query."
    (clojure.test/is
     (f/ok?
      (query-parsing/parse-query good-bed-file-query-params)))))
  
(deftest failing-bed-file-query
  (testing "Testing query parser for bad bed file query."
    (clojure.test/is
     (f/failed?
      (query-parsing/parse-query bad-bed-file-query-params)))))

(deftest successful-gtf-file-query
  (testing "Testing query parser for a good gtf file query."
    (clojure.test/is
     (f/ok?
      (query-parsing/parse-query good-gtf-file-query-params)))))
  
(deftest failing-gtf-file-query
  (testing "Testing query parser for bad gtf file query."
    (clojure.test/is
     (f/failed?
      (query-parsing/parse-query bad-gtf-file-query-params)))))
