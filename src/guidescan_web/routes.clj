(ns guidescan-web.routes
  "This namespace contains the core logic for routing in Guidescan.

  It exposes a REST api that returns a well formated list of
  guideRNAs pertaining to each input gene."
  (:require
   [ring.middleware.defaults :refer :all]
   [compojure.core :refer :all]
   [compojure.route :as route]
   [selmer.parser :as selmer]
   [failjure.core :as f]
   [cheshire.core :as cheshire]
   [ring.util.response :refer [content-type response not-found]]
   [taoensso.timbre :as timbre]
   [guidescan-web.query.render :as render]
   [guidescan-web.query.jobs :as jobs]))

(defn query-handler
  "Core of the Guidescan website. Exposes a REST handler that takes a
  query and returns a JSON reponse with the job id and status message
  as output.

  REST API:

  Endpoint: POST/GET /job/query/
  HTTP Response Code: 200 OK
  JSON Object: {:job-id id}
  "
  [job-queue req]
  (let [id (jobs/submit-query job-queue req)]
    (timbre/info "Submitted job for query from " (:remote-addr req))
    (cheshire/encode {:job-id id})))

(defn job-status-handler
  "Exposes a REST handler that returns the job's results in various
  formats when it is completed.

  REST API:

  Endpoint: GET /job/status/:id{[0-9]+}
  HTTP Response Code: 200 OK | 404 Not Found
  Response:
  {:job-status (:completed | :failed | :pending)
   :failure message}"
  [req job-queue job-id]
  (timbre/info "Job status request from " (:remote-addr req) " for id " job-id)
  (if-let [status (jobs/get-query-status job-queue job-id)]
    (let [failure-message (when (= status :failed) (f/message (jobs/get-query job-queue job-id)))
          json-obj {:job-status status :failure failure-message}]
      (content-type 
        (response (cheshire/encode json-obj))
        (render/get-content-type :json)))
    (not-found "Job ID not found.")))

(defn job-result-handler
  "Exposes a REST handler that returns a successful job's result in
  various formats when it is completed.

  REST API:

  Endpoint: GET /job/result/:format{csv|json|bed}/:id{[0-9]+}
  HTTP Response Code: 200 OK | 404 Not Found
  Response:
    JSON/BED/CSV containing query result"
  [req job-queue format job-id]
  (timbre/info "Job result request from " (:remote-addr req) " for id " job-id " with format " format)
  (if (= :completed (jobs/get-query-status job-queue job-id))
    (let [result (jobs/get-query job-queue job-id)]
       (when (f/ok? result)
         (content-type
          (response (render/render-query-result format result))
          (render/get-content-type format))))
    (not-found "Job result not found.")))

(defn supported-handler
  "Exposes a REST handler that returns the supported organisms and
  enzymes by this endpoint.

  REST API:

  Endpoint: GET /info/supported/
  HTTP Response Code: 200 OK
  JSON Response:
  {:supported-organisms [organisms]
   :supported-enzymes   [enzymes]}"
  [req config]
  (timbre/info "Info request from " (:remote-addr req) ".")
  (let [json-obj {:available-organisms (:available-organisms (:config config))
                  :available-enzymes   (:available-cas-enzymes (:config config))}]
    (content-type 
     (response (cheshire/encode json-obj))
     (render/get-content-type :json))))

(defn create-routes
  [config job-queue]
  (routes
   (ANY "/query" req (query-handler job-queue req))
   (GET "/job/status/:id{[0-9]+}" [id :as req]
        (job-status-handler req job-queue (Integer/parseInt id)))
   (GET "/job/result/:format{csv|json|bed}/:id{[0-9]+}" [format id :as req]
        (job-result-handler req job-queue (keyword format) (Integer/parseInt id)))
   (GET "/info/supported" req
        (supported-handler req config))
   (route/not-found "404 page not found.")))

(def www-defaults
  (-> site-defaults
      (assoc-in [:static :resources] "static")
      (assoc-in [:security :anti-forgery] false)))

(defn wrap-dir-index [handler]
  (fn [req]
    (handler
     (update-in req [:uri]
                #(if (= "/" %) "/home" %)))))

(defn wrap-cors
  "Wrap the server response in a Control-Allow-Origin Header to
  allow connections from the web app."
  [handler]
  (fn [request]
    (let [response (handler request)]
      (-> response
          (assoc-in [:headers "Access-Control-Allow-Origin"] "*")
          (assoc-in [:headers "Access-Control-Allow-Headers"] "x-requested-with")
          (assoc-in [:headers "Access-Control-Allow-Methods"] "*")))))

(defn handler [config job-queue]
  (-> (create-routes config job-queue)
      (wrap-cors)
      (wrap-defaults www-defaults)
      (wrap-dir-index)))
