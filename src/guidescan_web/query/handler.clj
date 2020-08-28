(ns guidescan-web.query.handler
  (:require [guidescan-web.query.jobs :as jobs]))

(defn newline-to-br [s]
  (clojure.string/replace s #"\r\n|\n|\r" "<br />\n"))

(defn query-handler
  "Core of the Guidescan website. Exposes a REST api that takes a query
  and returns a job queue submission as output."
  [job-queue req]
  (let [id (jobs/submit-query job-queue req)]
    (str "<html><body>Your job ID is: " id "<br />"
         "Go to the result <a href=\"/jobs/" id "\">here</a>"
          "</body></html>")))
