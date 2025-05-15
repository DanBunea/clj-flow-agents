(ns use-cases.tictactoe-processes.player-moves
  (:require
   [clojure.core.async.flow :as flow]))


(defn move
  ([] {:ins {:in "Movements asked by the board"
             :player "Movements done by the player"}
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
   (prn ::player msg)
   (cond
     (and (= in :in) (:ready state))
     (do
       (println "Player X, it is now your turn: ")
       (clojure.pprint/pprint msg)
       
       [state nil])
     
     (and (= in :player) (:ready state))
     [state {:board [msg]}]

     :else [state nil])))