(ns guidescan-web.routes
  "This namespace contains the core logic for routing in Guidescan.

  It exposes a REST api that returns a well formated list of
  guideRNAs pertaining to each input gene."
  (:require
   [ring.middleware.defaults :refer :all]
   [compojure.core :refer :all]
   [guidescan-web.query.parsing :as query-parsing]
   [guidescan-web.bam.db :as db]))

(defn newline-to-br [s]
  (clojure.string/replace s #"\r\n|\n|\r" "<br />\n"))

(defn query-route
  "Core of the Guidescan website. Exposes a REST api that takes a query
  in a variety of forms, parses it, and returns the response as a
  nested JSON object."
  [config req]
  (let [result (query-parsing/parse-query (:params req))
        organism (:organism (:params req))]
    (if-let [query (:success result)]
      (->> (map #(apply (partial db/query-bam config organism) %) query)
           (map #(clojure.string/join "\n" %)) 
           (clojure.string/join "\n\n\n")
           (newline-to-br))
      (newline-to-br (:failure result)))))

(defn create-routes
  [config]
  (routes
   (ANY "/query" req (query-route config req))
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

(defn handler [config]
  (-> (create-routes config)
      (wrap-defaults www-defaults)
      (wrap-dir-index)))
