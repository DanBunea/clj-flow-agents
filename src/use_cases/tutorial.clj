(ns use-cases.tutorial
  (:require
   [use-cases.tutorial-processes.crawl-files :as fc]
   [use-cases.tutorial-processes.identify-abstractions :as ia]
   [use-cases.tutorial-processes.analyze-relationships :as ar]
   [use-cases.tutorial-processes.order-chapters :as oc]
   [use-cases.tutorial-processes.write-chapters :as wc]
   [use-cases.tutorial-processes.combine-tutorial :as ct]
   [clojure.core.async :as a]
   [clojure.core.async.flow :as flow]
   [clojure.core.async.flow-monitor :as mon]))




(defn create-tutorial-flow
  []
  (flow/create-flow
   {:procs {:crawl-files             {:args {:max-file-size (* 1024 1024)}
                                      :proc (flow/process #'fc/load-files-and-content!)}
            :identify-abstractions   {:args {}
                                      :proc (flow/process #'ia/fetch-identified-abstractions!)}
            :analyze-relationships   {:args {}
                                      :proc (flow/process #'ar/generate-relationships!)}
            :order-chapters          {:args {}
                                      :proc (flow/process #'oc/fetch-chapter-order!)}
            :write-chapters          {:args {}
                                      :proc (flow/process #'wc/generate-the-chapters!)}
            :combine-tutorial        {:args {}
                                      :proc (flow/process #'ct/write-to-files!)}}
    :conns [[[:crawl-files :files] [:identify-abstractions :files]]
            [[:identify-abstractions :abstractions] [:analyze-relationships :abstractions]]
            [[:analyze-relationships :relationships] [:order-chapters :relationships]]
            [[:order-chapters :chapter-order] [:write-chapters :chapter-order]]
            [[:write-chapters :chapters] [:combine-tutorial :chapters]]]}))

;; Example usage:
(comment

  (def f (create-tutorial-flow))
  (def chs (flow/start f))
  (flow/resume f)
  @(flow/inject f [:crawl-files :trigger] [{:project-name "tutorial-clj" 
                                            :language "english"
                                            :directory "."
                                            :include-patterns #{"*.clj" "*.edn"}
                                            :exclude-patterns #{".portal/*" ".git/*" "output/*" ".calva/*" ".cpcache/*" ".clj-kondo/*"}}])
  (flow/pause f)
  (flow/stop f)
  (flow/ping f)
  (flow/ping-proc f :crawl-files)
  (flow/ping-proc f :order-chapters)
  (flow/ping-proc f :write-chapters)
  (flow/ping-proc f :combine-tutorial)
  (flow/ping-proc f :identify-abstractions) 

  (def server (mon/start-server {:flow f}))

  (mon/stop-server server)



  
  ;; Monitor messages on the report channel
  (a/go-loop []
    (when-let [msg (a/<! (:report-chan chs))]
      (println "REPORT:" msg)
      (recur)))

  ;; Check for errors
  (a/go-loop []
    (when-let [err (a/<! (:error-chan chs))]
      (println "ERROR:" err)
      (recur)))
  

nil)