(ns guidescan-web.routes
  "This namespace contains the core logic for routing in Guidescan.

  It exposes a REST api that returns a well formated list of
  guideRNAs pertaining to each input gene."
  (:require
   [ring.middleware.defaults :refer :all]
   [compojure.core :refer :all]
   [selmer.parser :as selmer]
   [failjure.core :as f]
   [cheshire.core :as cheshire]
   [ring.util.response :refer [content-type response]]
   [taoensso.timbre :as timbre]
   [guidescan-web.query.render :as render]
   [guidescan-web.query.jobs :as jobs]))

(defn query-handler
  "Core of the Guidescan website. Exposes a REST handler that takes a
  query and returns a JSON reponse with the job id and status message
  as output."
  [job-queue req]
  (let [id (jobs/submit-query job-queue req)]
    (timbre/info "Submited job for query from " (:remote-addr req))
    (cheshire/encode {:status :success
                      :data {:job-id id}})))

(defn job-status-handler
  "Exposes a REST handler that returns the job's results in various
  formats when it is completed."
  [job-queue job-id]
  (let [status (or (jobs/get-query-status job-queue job-id) :not-submitted)
        failure-message (when (= status :failed) (f/message (jobs/get-query job-queue job-id)))
        json-obj {:status :success
                  :data {:job-status status :failure failure-message}}]
    (content-type 
      (response (cheshire/encode json-obj))
      (render/get-content-type :json))))

(assoc {} 5 nil)

(defn job-result-handler
  "Exposes a REST handler that returns the job's results in various
  formats when it is completed."
  [job-queue format job-id]
  (if (= :completed (jobs/get-query-status job-queue job-id))
    (let [result (jobs/get-query job-queue job-id)]
       (when (f/ok? result)
         (content-type
          (response (render/render-query-result format result))
          (render/get-content-type format))))))

(defn create-routes
  [config job-queue]
  (routes
   (ANY "/query" req (query-handler job-queue req))
   (GET "/job/status/:id{[0-9]+}" [id]
        (job-status-handler job-queue (Integer/parseInt id)))
   (GET "/job/result/:format{csv|json|bed}/:id{[0-9]+}" [format id]
        (job-result-handler job-queue (keyword format) (Integer/parseInt id)))))

(def www-defaults
  (-> site-defaults
    (assoc-in [:static :resources] "static")
    (assoc-in [:security :anti-forgery] false)))

(defn wrap-dir-index [handler]
  (fn [req]
    (handler
     (update-in req [:uri]
                #(if (= "/" %) "/home" %)))))

(defn handler [config job-queue]
  (-> (create-routes config job-queue)
      (wrap-defaults www-defaults)
      (wrap-dir-index)))
