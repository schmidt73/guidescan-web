(ns guidescan-web.core
  (:require
   [com.stuartsierra.component :as component]
   [org.httpkit.server :as server]
   [taoensso.timbre :as timbre]
   [guidescan-web.query.jobs :as jobs]
   [guidescan-web.config :as config]
   [guidescan-web.routes :as routes]))
   
(defrecord WebServer [http-server config job-queue host port]
  component/Lifecycle
  (start [this]
    (timbre/info "Starting webserver component.")
    (when (nil? http-server)
      (assoc this :http-server
             (server/run-server (routes/handler config job-queue)
                                {:host host :port port}))))
  (stop [this]
    (when (not (nil? http-server))
      (timbre/info "Stopping webserver component.")
      (http-server)
      (assoc this :http-server nil))))

(defn web-server [host port]
  (map->WebServer {:host host :port port}))

(defn core-system []
  (component/system-map
   :web-server (component/using (web-server "localhost" 8000) [:config :job-queue])
   :job-queue (component/using (jobs/create-job-queue) [:config])
   :config (config/create-config "config.edn")))

(defn -main
  [& args]
  (let [system (component/start (core-system))
        lock (promise)
        stop (fn []
               (component/stop system)
               (deliver lock :release))]
    (.addShutdownHook (Runtime/getRuntime) (Thread. stop))
    @lock
    (System/exit 0)))
