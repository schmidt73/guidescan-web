(ns guidescan-web.routes-test
  (:require [clojure.test :refer :all]
            [guidescan-web.routes :as routes]))

(deftest gRNA-query
  (testing "Test guideRNA Query Handler"
    (clojure.test/is
     (not (nil? (routes/query-route
                 {:params {:query "chrIV:911770-916325"
                           :organism "ce11" :enzyme "cas9"
                           :mode "within" :flank-size 1000
                           :ordering "offtargets" :display "all"
                           :top-n-results 3}}))))))


