(ns use-cases.tictactoe-processes.board
  (:require
   [clojure.core.async.flow :as flow]))

(defn- check-line [line]
  (when (and (apply = line) (first line))
    (first line)))

(defn- get-cols [board]
  (apply mapv vector board))

(defn- get-diagonals [board]
  [[(get-in board [0 0]) (get-in board [1 1]) (get-in board [2 2])]
   [(get-in board [0 2]) (get-in board [1 1]) (get-in board [2 0])]])



(defn board-state [board]
  (let [lines (concat board (get-cols board) (get-diagonals board))
        winning-player (some check-line lines)
        status (if winning-player
                 winning-player
                 (when (not (some nil? (flatten board)))
                   :draw))]
    (cond
      (= status :x) {:winner :x, :finished? true}
      (= status :0) {:winner :0, :finished? true}
      (= status :draw) {:result nil, :finished? true}
      (nil? status) {:result nil, :finished? false})))

(comment
  (board-state [[:0 :x :0] [nil :x nil] [:0 :x nil]])
  (board-state [[:x :x :x] [nil :0 nil] [:0 nil nil]])
  (board-state [[:0 :x :0] [:0 :x nil] [:0 :x nil]])
  (board-state [[:x :0 :x] [:0 :x :0] [:x :0 :x]])
  (board-state [[:x :0 :0] [:x :x :0] [:0 :0 :x]])
  (board-state [[:x :0 :x] [:x :0 :0] [:0 :x :x]]) ;; stalemate
  (board-state [[nil nil nil] [nil nil :0] [nil nil :x]]))


(defn choose-player
  ([] {:ins {:movements "Movements"}
       :outs {:llm "when llm needs to move"
              :player "when the player needs to move"}})
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
   (prn ::board msg)
   (if (and (= in :movements) (:ready state))
     (let [no-of-moves (->> (:board msg)
                               (flatten)
                               (filter #{:x :0})
                               count
                               
                               )
           player-to-make-the-move (if (even? no-of-moves) :x :0)
           new-board (-> msg
                         :board
                         (assoc-in (:move msg) player-to-make-the-move))
           new-board-state (board-state new-board)
           new-msg (-> msg
                       (assoc :board new-board))]
       
       [state (cond 
                (:finished? new-board-state)
                (do
                  (println 777 new-board-state )
                  (println 777 new-board)
                  [state nil])
                
                (= :x player-to-make-the-move) 
                {:llm [new-msg]}

                :else 
                {:player [new-msg]}
                )])
     [state nil]))
  )





(comment 
  (->> [[nil nil :x] [:0 :x nil] [nil nil nil]]
       (flatten)
       (filter #{:x :0})
       count)

  )