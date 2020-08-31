(ns guidescan-web.query.jobs
  "This namespaces exposes a JobQueue component that asynchronously performs
  gRNA query jobs in the background."
  (:require [guidescan-web.query.process :refer [process-query]]
            [failjure.core :as f]
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
  
(defn submit-query
  "Submits the query to the job quere and returns its ID."
  [job-queue req]
  (dosync
   (let [jobs (:jobs job-queue)
         job-id (:id-counter @jobs)
         config (:config job-queue)
         future-obj (future-call #(process-query config req))]
      (ref-set jobs
        (assoc @jobs
               :id-counter (inc job-id)
               job-id {:future future-obj
                       :timestamp (java.time.LocalDateTime/now)}))
      job-id)))

(defn get-query-status
  "Gets the status for a query. Returns nil if job-id does not exist."
  [job-queue id]
  (if-let [job (get @(:jobs job-queue) id)]
    (if (future-done? (:future job))
      (if (f/failed? @(:future job))
        :failed
        :completed)
      :pending)))

(defn get-query
  "Gets the result of a query (blocking). Returns nil if job does not
  exist."
  [job-queue id]
  (if-let [job (get @(:jobs job-queue) id)]
    @(:future job)))
