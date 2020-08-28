(ns guidescan-web.query.jobs
  (:require [guidescan-web.query.parsing :refer [parse-query]]
            [guidescan-web.bam.db :as db]
            [com.stuartsierra.component :as component]))

(defrecord JobQueue [config jobs]
  component/Lifecycle
  (start [this]
    (when (nil? jobs)
      (assoc this :jobs (ref {:id-counter 0})))) ; could have used atom but easier to use ref
  (stop [this]))

(defn create-job-queue
  []
  (map->JobQueue {}))
  
(defn- perform-parsed-query
  [config organism parsed-queries]
  (loop [idx 0 results []]
    (let [query (nth parsed-queries idx)
          result (try
                   (apply db/query-bam-grna-db config organism query)
                   (catch java.lang.IllegalArgumentException _
                      {:error (str "Invalid chromosone name: " (first query))}))]
      (if (or (some? (:error result))
              (= (+ 1 idx) (count parsed-queries)))
        result
        (recur (inc idx) (conj results result))))))

(defn- perform-query
  "Performs the query, returning either a response vector or an error
  message."
  [config req]
  (let [result (parse-query (:params req))
        organism (:organism (:params req))]
    (if-let [query (:success result)] ; else branch = parse error
      (perform-parsed-query config organism query)
      {:error (:failure result)})))

(defn submit-query
  "Submits the query to the job query and returns its ID."
  [job-queue req]
  (dosync
   (let [jobs (:jobs job-queue)
         job-id (:id-counter @jobs)
         config (:config job-queue)
         future-obj (future-call #(perform-query config req))]
      (ref-set jobs
        (assoc @jobs
               :id-counter (inc job-id)
               job-id {:future future-obj
                       :timestamp (java.time.LocalDateTime/now)}))
      job-id)))

(defn get-job-status
  "Gets the status for a job. Returns nil if job does not exist."
  [job-queue id]
  (if-let [job (get @(:jobs job-queue) id)]
    (if (future-done? (:future job))
      :completed
      :pending)))

(defn get-job
  "Gets the result of a job (blocking). Returns nil if job does not
  exist."
  [job-queue id]
  (if-let [job (get @(:jobs job-queue) id)]
    @(:future job)))
