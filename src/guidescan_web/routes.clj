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
   [guidescan-web.genomics.resolver :as resolver]
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

  Endpoint: GET /job/status/:id
  HTTP Response Code: 200 OK | 404 Not Found
  Response:
  {:job-status (:completed | :failed | :pending)
   :result-type (:grna | :standard) // ONLY WHEN COMPLETED
   :failure message}"
  [req job-queue job-id]
  (timbre/info "Job status request from " (:remote-addr req) " for id " job-id)
  (if-let [status (jobs/get-query-status job-queue job-id)]
    (if (= status :completed)
      (let [result-type (:query-type (jobs/get-query job-queue job-id))
            json-obj {:job-status status :result-type result-type}]
        (content-type 
          (response (cheshire/encode json-obj))
          (render/get-content-type :json)))
      (let [failure-message (when (= status :failed) (f/message (jobs/get-query job-queue job-id)))
            json-obj {:job-status status :failure failure-message}]
        (content-type 
          (response (cheshire/encode json-obj))
          (render/get-content-type :json))))
    (not-found "Job ID not found.")))

(defn job-result-handler
  "Exposes a REST handler that returns a successful job's result in
  various formats when it is completed.

  REST API:

  Endpoint: GET /job/result/:format{csv|json|bed|excel}/:id{[0-9]+}
  HTTP Response Code: 200 OK | 404 Not Found
  Response:
    JSON/BED/CSV containing query result"
  [req job-queue format job-id]
  (timbre/info "Job result request from " (:remote-addr req) " for id " job-id " with format " format)
  (if (= :completed (jobs/get-query-status job-queue job-id))
    (let [result (jobs/get-query job-queue job-id)]
       (when (f/ok? result)
         (content-type
          (response (render/render-query-result req format result))
          (render/get-content-type format))))
    (not-found "Job result not found.")))

(defn autocomplete-handler
  "Exposes a REST handler that autocomplets gene symbols.

  REST API:

  Endpoint: GET /autocomplete/symbol/
  HTTP Response Code: 200 OK
  JSON Response:
  [matching-gene-symbols]"
  [gene-resolver req organism symbol]
  (timbre/info "Gene symbol resolution request from " (:remote-addr req) ".")
  (let [suggestions (resolver/resolve-gene-symbol-suggestion gene-resolver organism symbol)]
    (content-type 
     (response (cheshire/encode suggestions))
     (render/get-content-type :json))))

(defn supported-handler
  "Exposes a REST handler that returns the supported organisms and
  enzymes by this endpoint.

  REST API:

  Endpoint: GET /info/supported/
  HTTP Response Code: 200 OK
  JSON Response:
  {:supported [{:organism :enzyme}]}"
  [req config]
  (timbre/info "Info request from " (:remote-addr req) ".")
  (let [json-obj {:available (keys (:grna-database-path-map (:config config)))}]
    (content-type 
     (response (cheshire/encode json-obj))
     (render/get-content-type :json))))

(defn examples-handler
  "Exposes a REST handler that returns example queries for
  each organism

  REST API:

  Endpoint: GET /info/examples/
  HTTP Response Code: 200 OK
  JSON Response:
  {:examples {:organism {:enzyme {:page}}}}"
  [req config]
  (timbre/info "Examples request from " (:remote-addr req) ".")
  (let [json-obj (:examples (:config config))]
    (content-type 
     (response (cheshire/encode json-obj))
     (render/get-content-type :json))))

(defn create-routes
  [config job-queue gene-resolver]
  (routes
   (ANY "/query" req (query-handler job-queue req))
   (GET "/job/status/:id" [id :as req]
        (job-status-handler req job-queue id))
   (ANY "/job/result/:format{csv|json|bed|excel}/:id" [format id :as req]
        (job-result-handler req job-queue (keyword format) id))
   (GET "/info/examples" req
        (examples-handler req config))
   (GET "/info/supported" req
        (supported-handler req config))
   (GET "/info/autocomplete" [organism symbol :as req]
        (autocomplete-handler gene-resolver req organism symbol))
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

(defn handler [config job-queue gene-resolver]
  (-> (create-routes config job-queue gene-resolver)
      (wrap-cors)
      (wrap-defaults www-defaults)
      (wrap-dir-index)))
