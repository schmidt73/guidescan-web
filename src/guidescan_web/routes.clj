(ns guidescan-web.routes
  "This namespace contains the core logic for routing in Guidescan.

  It exposes a REST api that returns a well formated list of
  guideRNAs pertaining to each input gene."
  (:require
   [ring.middleware.defaults :refer :all]
   [compojure.core :refer :all]
   [guidescan-web.query.jobs :as jobs]))

(defn query-handler
  "Core of the Guidescan website. Exposes a REST api that takes a query
  and returns a job queue submission as output."
  [job-queue req]
  (let [id (jobs/submit-query job-queue req)]
    (str "<html><body>Your job ID is: " id "<br />"
         "Go to the result <a href=\"/jobs/" id "\">here</a>"
          "</body></html>")))

(defn create-routes
  [config job-queue]
  (routes
   (ANY "/query" req (query-handler job-queue req))
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
