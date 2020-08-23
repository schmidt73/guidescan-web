(ns guidescan-web.core
  (:require
   [com.stuartsierra.component :as component]
   [org.httpkit.server :as server]
   [guidescan-web.routes :as routes]))
   
(defrecord WebServer [http-server host port]
  component/Lifecycle
  (start [this]
    (when (nil? http-server)
      (assoc this :http-server
             (server/run-server routes/handler {:host host
                                                :port port}))))
  (stop [this]
    (when (not (nil? http-server))
      (http-server)
      (assoc this :http-server nil))))

(defn web-server [host port]
  (map->WebServer {:host host :port port}))

(defn core-system []
  (component/system-map :web-server (web-server "localhost" 8000)))
