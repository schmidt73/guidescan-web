(ns guidescan-web.bam.db-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [guidescan-web.config :as config]
            [guidescan-web.bam.db :as db]))

(defmacro with-component [name component & body]
  `(let [~name (component/start ~component)]
     (try
       ~@body
       (finally
         (component/stop ~name)))))

(defn test-config []
  (config/create-config "test_config.edn"))

(deftest grna-successful-query-test
  (testing "gRNA query test"
    (is
     (with-component config (test-config)
       (let [grnas (db/query-bam-grna-db config "ce11" "chrIV" 911770 911820)
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
