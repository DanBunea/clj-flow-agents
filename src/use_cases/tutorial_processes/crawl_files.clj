(ns use-cases.tutorial-processes.crawl-files
  (:require 
   [clojure.core.async.flow :as flow]
   [adapters.filesystem.files :refer [crawl-local-files]]
   [adapters.logging.log :as l]))




(defn load-files-and-content!
  "Process that crawls files in a directory"
  ([] {:params {:directory "Directory to crawl"
                :include-patterns "File patterns to include"
                :exclude-patterns "File patterns to exclude"
                :max-file-size "Maximum file size in bytes"}
       :ins {:trigger "Input to trigger file crawling"}
       :outs {:files "Map of file paths to contents"}})

  ;; init
  ([args] args)

  ;; transition
  ([state transition]
   (case transition
     ::flow/resume
     (assoc state :ready true)

     ::flow/pause
     state

     ::flow/stop
     state))

  ;; transform
  ([state in msg]
   (if (and (= in :trigger) (:ready state))
     (let [files (:files (crawl-local-files (:directory msg)
                                           :include-patterns (:include-patterns msg)
                                           :exclude-patterns (:exclude-patterns msg)
                                           :max-file-size (:max-file-size state)))]
       (l/log "Files fetched")
       [state {:files [(assoc msg :files files)]}])
     [state nil])))