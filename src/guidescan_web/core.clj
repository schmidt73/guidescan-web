(ns guidescan-web.core
  (:gen-class)
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.tools.cli :as cli]
   [com.stuartsierra.component :as component]
   [org.httpkit.server :as server]
   [taoensso.timbre :as timbre]
   [guidescan-web.genomics.annotations :as annotations]
   [guidescan-web.bam.db :as db]
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

(defn core-system [host port config-file]
  (component/system-map
   :bam-db (component/using (db/create-bam-db) [:config])
   :web-server (component/using (web-server host port) [:config :job-queue])
   :job-queue (component/using (jobs/create-job-queue) [:bam-db :gene-annotations])
   :gene-annotations (component/using (annotations/gene-annotations) [:config])
   :config (config/create-config config-file)))

(defn start-system
  "Starts the core system, blocking until it returns."
  [host port config-file]
  (let [system (component/start (core-system host port config-file))
          lock (promise)
          stop (fn []
                 (component/stop system)
                 (deliver lock :release))]
      (.addShutdownHook (Runtime/getRuntime) (Thread. stop))
      @lock
      (System/exit 0)))

;;;; CLI Stuff

(def cli-options
  [["-p" "--port PORT" "Port number"
    :default 8000
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000)
               "Invalid port number, must be between 0 and 65536"]]
   ["-H" "--hostname HOST" "Hostname"
    :default "localhost"
    :validate [#(try
                  (java.net.InetAddress/getByName %)
                  true
                  (catch java.net.UnknownHostException _ false))
               "Invalid hostname."]]
   ["-c" "--config CONFIG" "EDN file for program configuration."
    :validate [#(try
                  (edn/read-string (slurp %))
                  true
                  (catch java.lang.RuntimeException _ false))
               "Config file is not in valid EDN format. Look at
               example configuration."]]
   ["-v" nil "Verbosity level"
    :id :verbosity
    :default 1
    :update-fn inc]
   [nil "--example-config" "Prints out an example configuration."]
   ["-h" "--help" "Displays this help message."]])

(defn usage [options-summary]
  (->> ["Guidescan 2.0 Webserver"
        ""
        "Usage: java -jar guidescan-web.jar [options] -c CONFIG "
        ""
        "Options:"
        options-summary
        ""
        "Please refer to https://github.com/schmidt73/guidescan-web page for more information."]
       (clojure.string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (clojure.string/join \newline errors)))

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with an error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options)
      {:exit-message (usage summary) :ok? true}

      (:example-config options)
      {:exit-message (slurp (io/resource "example_config.edn")) :ok? true}

      errors
      {:exit-message (error-msg errors)}

      (every? #(contains? options %) [:port :hostname :config])
      {:options options}

      :else
      {:exit-message (usage summary)})))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (let [{:keys [options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (start-system (:hostname options) (:port options) (:config options)))))
