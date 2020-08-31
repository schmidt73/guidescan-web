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
   [guidescan-web.query.render :as render]
   [guidescan-web.query.jobs :as jobs]))

(defn query-handler
  "Core of the Guidescan website. Exposes a REST api that takes a query
  and returns a job queue submission as output."
  [job-queue req]
  (prn req)
  (let [id (jobs/submit-query job-queue req)]
    (selmer/render-file "static/query.html" {:job-id id})))

(defn job-show-handler
  "Displays the results of a job."
  [job-queue job-id]
  (let [status (jobs/get-query-status job-queue job-id)]
    (selmer/render-file
      "static/jobs.html"
      (assoc
       {:status status :job-id job-id}
       :error-message
       (when (= status :failed) (f/message (jobs/get-query job-queue job-id)))))))

(defn job-get-handler
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
   (GET "/job/show/:id{[0-9]+}" [id]
        (job-show-handler job-queue (Integer/parseInt id)))
   (GET "/job/get/:format{csv|json}/:id{[0-9]+}" [format id]
        (job-get-handler job-queue (keyword format) (Integer/parseInt id)))
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
