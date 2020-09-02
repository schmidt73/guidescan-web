(ns guidescan-web.genomics.annotations-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [guidescan-web.mock :as mock]
            [guidescan-web.genomics.annotations :as annotate]))

(deftest chrIV-correct-annotations
  (testing "chrIV annoations."
    (mock/with-component-or-system system (mock/test-system-no-www)
      (let [ga (:gene-annotations system)
            annotations
            (annotate/get-annotations ga "ce11" "chrIV" 315379 315401)]
        (is
         (= "dsc-4" (second (first annotations))))))))

(deftest chrII-correct-annotations
  (testing "chrII annoations."
    (mock/with-component-or-system system (mock/test-system-no-www)
      (let [ga (:gene-annotations system)
            annotations
            (annotate/get-annotations ga "ce11" "chrII" 310244 310266)]
        (is (= "mps-2" (second (first annotations))))))))

