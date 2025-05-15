(ns use-cases.tictactoe
  (:require
   [use-cases.tictactoe-processes.board :as b]
   [use-cases.tictactoe-processes.llm-moves :as lm]
   [use-cases.tictactoe-processes.player-moves :as pm]
   
   [clojure.core.async :as a]
   [clojure.core.async.flow :as flow]
   [clojure.core.async.flow-monitor :as mon]))


(defn create-tictactoe-flow
  []
  (flow/create-flow
   {:procs {:board                    {:proc (flow/process #'b/choose-player)}
            :player-moves             {:proc (flow/process #'pm/move)}
            :llm-moves                {:proc (flow/process #'lm/move!)}
            :output                {:proc (flow/process #'lm/move!)}
            }
    :conns [[[:board :player] [:player-moves :in]]
            [[:board :llm] [:llm-moves :in]]
            [[:llm-moves :board] [:board :movements]]
            [[:player-moves :board] [:board :movements]]]}))


(comment

  (def f (create-tictactoe-flow))

  (def chs (flow/start f))
  (flow/resume f)
  @(flow/inject f [:player-moves :player] [{:board [[nil nil nil] [nil nil nil] [nil nil nil]]
                                            :move [1 1]}])

  @(flow/inject f [:player-moves :player] [{:board [[:0 nil nil] [nil :x nil] [nil nil nil]], :move [0 1]}])
  @(flow/inject f [:player-moves :player] [{:board [[:0 :x nil] [nil :x nil] [:0 nil nil]], :move [2 1]}])
  

  (flow/pause f)
  (flow/stop f)
  (flow/ping f)


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


  nil
  )