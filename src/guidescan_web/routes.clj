(ns guidescan-web.routes
  "This namespace contains the core logic for routing in Guidescan.

  It exposes a REST api that returns a well formated list of
  guideRNAs pertaining to each input gene."
  (:require
   [ring.middleware.defaults :refer :all]
   [compojure.core :refer :all]
   [guidescan-web.query.jobs :as jobs]
   [guidescan-web.query.handler :refer [query-handler]]))

(defn get-job-status
  [job-queue id]
  (if-let [status (jobs/get-job-status job-queue id)]
    (if (= status :completed)
      (str (jobs/get-job job-queue id))
      "Job is still pending... come back later!")
    (str "There is no job with ID: " id)))

(defn create-routes
  [config job-queue]
  (routes
   (ANY "/query" req (query-handler job-queue req))
   (GET "/jobs/:id{[0-9]+}" [id] (get-job-status job-queue (Integer/parseInt id)))
   (GET "/" [] ())))

(def www-defaults
  (-> site-defaults
    (assoc-in [:static :resources] "static")
    (assoc-in [:security :anti-forgery] false)))

(defn wrap-dir-index [handler]
  (fn [req]
    (handler
     (update-in req [:uri]
                #(if (= "/" %) "/index.html" %)))))

(defn handler [config job-queue]
  (-> (create-routes config job-queue)
      (wrap-defaults www-defaults)
      (wrap-dir-index)))
