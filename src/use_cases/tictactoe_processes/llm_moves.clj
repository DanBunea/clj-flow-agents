(ns use-cases.tictactoe-processes.llm-moves
  (:require
   [adapters.openai.api :as oai]
    [clojure.core.async.flow :as flow]))
  
  
  (defn move!
    ([] {:ins {:in "Movements"}
         :outs {:board "send to board"}})
    ([args] (assoc args :ready true))
    ([state transition]
     (case transition
       ::flow/resume
       (assoc state :ready true)
  
       ::flow/pause
       (assoc state :ready false)
  
       ::flow/stop
       (assoc state :ready false)
  
       state))
  
    ([state in msg]
     (prn ::llm-moves msg)
     (if (and (= in :in) (:ready state))
       (let [next-move (oai/move! (:board msg) (:move msg))]
         [state {:board [(assoc msg :move next-move)]}])
       [state nil])))