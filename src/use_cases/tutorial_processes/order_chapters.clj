(ns use-cases.tutorial-processes.order-chapters
  (:require 
   [clojure.core.async.flow :as flow]
   [clojure.string :as str]
   [clojure.data.json :as json]
   [adapters.logging.log :as l]
   [adapters.gemini.llm :refer [stream-llm]]))

(defn order-chapters!
  "Determine the optimal chapter order for explaining abstractions.
   
   Args:
     abstractions - Vector of abstraction maps with :name, :description, and :files keys
     relationships - Map with :summary and :details keys about abstraction relationships
     project-name - String name of the project
     language - Optional string language code (defaults to 'english')
   
   Returns:
     Vector of indices representing the optimal chapter order"
  [{:keys [abstractions relationships project-name language]} ]
  (let [language (or language "english")
        
        ;; Create abstraction listing with indices
        abstraction-listing 
        (str/join "\n"
                  (map-indexed 
                   (fn [i abstr]
                     (str "- " i " # " (:name abstr)))
                   abstractions))
        
        ;; Get the project summary and relationship details
        project-summary (:summary relationships)
        
        ;; Create context with relationship information
        relationships-context
        (str "Project Summary:\n" project-summary "\n\n"
             "Relationships (Indices refer to abstractions above):\n"
             (str/join "\n"
                       (map (fn [rel]
                              (let [from-name (get-in abstractions [(:from rel) :name])
                                    to-name (get-in abstractions [(:to rel) :name])]
                                (format "- From %d (%s) to %d (%s): %s" 
                                        (:from rel) from-name 
                                        (:to rel) to-name 
                                        (:label rel))))
                            (:details relationships))))
        
        ;; Add language-specific notes if not English
        list-lang-note 
        (when (not= (str/lower-case language) "english")
          (format " (Names might be in %s)" (str/capitalize language)))
        
        ;; Create the prompt
        prompt (format "
Given the following project abstractions and their relationships for the project `%s`:

Abstractions (Index # Name)%s:
%s

Context about relationships and project summary:
%s

If you are going to make a tutorial for `%s`, what is the best order to explain these abstractions, from first to last?
Ideally, first explain those that are the most important or foundational, perhaps user-facing concepts or entry points. Then move to more detailed, lower-level implementation details or supporting concepts.

Output the ordered list of abstraction indices, including the name in a comment for clarity. Use the format `idx # AbstractionName`.

Format the output as a JSON array:

```json
[
  \"2 # FoundationalConcept\",
  \"0 # CoreClassA\",
  \"1 # CoreClassB (uses CoreClassA)\"
]
```

Now, provide the JSON output:"
                     project-name
                     list-lang-note
                     abstraction-listing
                     relationships-context
                     project-name)
        
        ;; Call LLM and extract response
        _ (l/log "Determining chapter order using LLM...")
        
        response (stream-llm
                  prompt
                  (fn [chunk]
                    (l/log chunk)
                    (flush)))
        
        
        ;; Extract JSON content
        json-str (-> response
                     (str/split #"```json")
                     second
                     (str/split #"```")
                     first
                     str/trim)
        
        ;; Parse JSON to get ordered chapters
        json-data (json/read-str json-str :key-fn keyword)
        
        ;; Validate the order is a list
        _ (when-not (sequential? json-data)
            (throw (ex-info "LLM output is not a list" {:received json-data})))
        
        ;; Extract indices from the ordered list
        num-abstractions (count abstractions)
        ordered-indices 
        (loop [items json-data
               result []
               seen-indices #{}]
          (if (empty? items)
            result
            (let [current (first items)
                  idx (try
                        (cond
                          (integer? current) current
                          (and (string? current) (.contains ^String current "#"))
                          (-> current
                              (str/split #"#")
                              first
                              str/trim
                              Integer/parseInt)
                          :else (Integer/parseInt (str/trim (str current))))
                        (catch Exception e
                          (l/log "Warning: Could not parse index from" current)
                          nil))]
              
              (if (and idx 
                       (<= 0 idx (dec num-abstractions))
                       (not (contains? seen-indices idx)))
                ;; Valid index, add it and continue
                (recur (rest items) 
                       (conj result idx) 
                       (conj seen-indices idx))
                ;; Invalid or duplicate index, skip
                (recur (rest items) result seen-indices)))))]
    
    ;; Check if we have all abstractions
    (when (not= (count ordered-indices) num-abstractions)
      (l/log "Warning: Missing some abstractions in ordered list. Expected" 
           num-abstractions "got" (count ordered-indices)))
    
    ordered-indices))

(defn fetch-chapter-order!
  "Process that determines the best order for explaining abstractions"
  ([] {
       :ins {:relationships "Information about abstractions and their relationships"}
       :outs {:chapter-order "Ordered list of abstraction indices"}})

  ;; init
  ([args] (assoc args :ready true))

  ;; transition
  ([state transition]
   (case transition
     ::flow/resume
     (assoc state :ready true)

     ::flow/pause
     (assoc state :ready false)

     ::flow/stop
     (assoc state :ready false)
     
     state))

  ;; transform
  ([state in msg]
   (if (and (= in :relationships) (:ready state))
     [state {:chapter-order [(assoc msg :chapter-order (order-chapters! (select-keys msg [:abstractions :relationships :project-name :language])))]}]
     [state nil]))) 