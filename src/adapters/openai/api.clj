(ns adapters.openai.api
(:require 
 [clojure.string :as str] 
 [wkok.openai-clojure.api :as api]))

(defn move! [board my-move]
  (let [result (-> (api/create-chat-completion {:model "gpt-3.5-turbo"
                                                :messages [{:role "system" :content "You are a helpful assistant, helping the user play Tic Tac Toe."}
                                                           {:role "user" :content
                                                            (str "
               I am playing tic tac toe, and my board, in Clojure's end is:
               ```clojure
               " (with-out-str (clojure.pprint/pprint board)) "
               ```
               
                   after I made the move at 
               ```clojure
               " (with-out-str (clojure.pprint/pprint my-move)) "
               ``` where we represent in the tuple the row and the column indexes.
               . Could you return a clojure tuple with the next move for :o in markdown? and only it in clojure format? Do not return anything else. ")}]}))]
    (-> result
        :choices
        first  :message
        :content
        (str/split #"```clojure")
        second
        (str/split #"```")
        first
        str/trim
        read-string)))


(comment
  
  (move! {:board [[:o :x nil] 
                  [nil :x nil]
                  [:o :x nil]]} [2 1]) 

  )