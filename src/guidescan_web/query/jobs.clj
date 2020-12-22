(ns guidescan-web.query.jobs
  "This namespaces exposes a JobQueue component that asynchronously performs
  gRNA query jobs in the background."
  (:require [guidescan-web.query.process :refer [process-query]]
            [failjure.core :as f]
            [taoensso.timbre :as timbre]
            [com.stuartsierra.component :as component]))

(defrecord JobQueue [bam-db gene-annotations
                     gene-resolver sequence-resolver
                     jobs max-age]
  component/Lifecycle
  (start [this]
    (when (nil? jobs)
      (timbre/info "Initializing job queue.")
      (assoc this :jobs (ref {:id-counter 0})))) ; could have used atom but easier to use ref
  (stop [this]
    (timbre/info "Cleaning up job queue.")))

(defn remove-job
  "Removes a job from the job-queue."
  [job-queue id]
  (dosync
   (let [jobs (:jobs job-queue)]
     (ref-set jobs
       (dissoc @jobs id)))))

(defn create-job-removal-task
  [job-queue id]
  (proxy [java.util.TimerTask] []
      (run [] (remove-job job-queue id))))

(defn schedule-job-removal
  "Schedules a job to be removed from the job queue after it becomes
  too old."
  [job-queue id]
  (let [age (:max-age job-queue)]
    (.schedule
     (new java.util.Timer)
     (create-job-removal-task job-queue id)
     (-> (java.time.LocalDateTime/now)
         (.plusSeconds (get age :seconds 0))
         (.plusMinutes (get age :minutes 0))
         (.plusHours   (get age :hours 0))
         (.plusDays    (get age :days 0))
         (.atZone (java.time.ZoneId/systemDefault))
         (.toInstant)
         (java.util.Date/from)))))

(defn create-job-queue
  "Creates a job queue where max-age defines the how old a job can get
  before being deleted from the queue.

  max-age is a map taking the form:

     {:days D :hours H
      :minutes M :seconds S},

  All fields are optional and if not specified default to 0."
  [max-age]
  (map->JobQueue {:max-age max-age}))
  
(defn submit-query
  "Submits the query to the job queue and returns its ID."
  [job-queue req]
  (dosync
   (let [jobs (:jobs job-queue)
         job-id (:id-counter @jobs)
         process-args {:gene-annotations (:gene-annotations job-queue)
                       :bam-db (:bam-db job-queue)
                       :gene-resolver (:gene-resolver job-queue)
                       :sequence-resolver (:sequence-resolver job-queue)}
         future-obj (future-call #(process-query process-args req))]
      (ref-set jobs
        (assoc @jobs
               :id-counter (inc job-id)
               job-id {:future future-obj
                       :timestamp (java.time.LocalDateTime/now)}))
      (schedule-job-removal job-queue job-id)
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
