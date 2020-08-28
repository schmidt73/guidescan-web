(ns guidescan-web.query.handler
  (:require [guidescan-web.query.parsing :refer [parse-query]]
            [guidescan-web.bam.db :as db]))

(defn newline-to-br [s]
  (clojure.string/replace s #"\r\n|\n|\r" "<br />\n"))

(defn query-handler
  "Core of the Guidescan website. Exposes a REST api that takes a query
  in a variety of forms, parses it, and returns the response as a
  nested JSON object."
  [config req]
  (let [result (parse-query (:params req))
        organism (:organism (:params req))]
    (if-let [query (:success result)]
      (map #(apply (partial db/query-bam-grna-db config organism) %) query))))
