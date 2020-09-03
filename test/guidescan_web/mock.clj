(ns guidescan-web.mock
  (:require [com.stuartsierra.component :as component]
            [guidescan-web.config :as config]
            [guidescan-web.genomics.annotations :as annotations]
            [guidescan-web.bam.db :as db]
            [guidescan-web.query.jobs :as jobs]))

(defmacro with-component-or-system [name component & body]
  `(let [~name (component/start ~component)]
     (try
       ~@body
       (finally
         (component/stop ~name)))))

(defn test-config []
  (config/create-config "test_config.edn"))

(defn test-system-no-www []
  (component/system-map
   :bam-db (component/using (db/create-bam-db) [:config])
   :config (test-config)
   :gene-annotations (component/using (annotations/gene-annotations)
                                      [:config])
   :job-queue (component/using (jobs/create-job-queue) [:bam-db :gene-annotations])))
