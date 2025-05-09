(ns use-cases.tutorial-processes.identify-abstractions
  (:require
   [clojure.string :as str]
   [clojure.data.json :as json]
   [clojure.core.async.flow :as flow]
   [adapters.gemini.llm :refer [stream-llm]]
   [use-cases.tutorial-processes.files-llm-context :refer [->llm-context]]
   [adapters.logging.log :as l]))




(defn identify-abstractions!
  "Identify core abstractions from the codebase context.
   
   Args:
     context-map - Map containing :context and :file-info from ->llm-context
     project-name - String name of the project
     language - Optional string language code (defaults to 'english')
   
   Returns:
     Vector of maps with :name, :description, and :files keys"
  [{:keys [context file-info]} project-name & [language]]
  (let [language (or language "english")
        file-listing (str/join "\n"
                               (map (fn [[idx path]]
                                      (format "- %d # %s" idx path))
                                    file-info))
        file-count (count file-info)

        ;; Add language instruction and hints only if not English
        language-instruction (when (not= (str/lower-case language) "english")
                               (format "IMPORTANT: Generate the `name` and `description` for each abstraction in **%s** language. Do NOT use English for these fields.\n\n"
                                       (str/capitalize language)))
        name-lang-hint (when (not= (str/lower-case language) "english")
                         (format " (value in %s)" (str/capitalize language)))
        desc-lang-hint (when (not= (str/lower-case language) "english")
                         (format " (value in %s)" (str/capitalize language)))

        prompt (format "
For the project `%s`:

Codebase Context:
%s

%sAnalyze the codebase context.
Identify the top 5-10 core most important abstractions to help those new to the codebase.

For each abstraction, provide:
1. A concise `name`%s.
2. A beginner-friendly `description` explaining what it is with a simple analogy, in around 100 words%s.
3. A list of relevant `file_indices` (integers) using the format `idx # path/comment`.

List of file indices and paths present in the context:
%s

Format the output as a JSON array of objects:

```json
[
  {
    \"name\": \"Query Processing%s\",
    \"description\": \"Explains what the abstraction does. It's like a central dispatcher routing requests.%s\",
    \"file_indices\": [0, 3]
  },
  {
    \"name\": \"Query Optimization%s\",
    \"description\": \"Another core concept, similar to a blueprint for objects.%s\",
    \"file_indices\": [5]
  }
]
```"
                       project-name
                       context
                       (or language-instruction "")
                       (or name-lang-hint "")
                       (or desc-lang-hint "")
                       file-listing
                       (or name-lang-hint "")
                       (or desc-lang-hint "")
                       (or name-lang-hint "")
                       (or desc-lang-hint ""))

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

        

        ;; Parse JSON
        abstractions (json/read-str json-str :key-fn keyword)
        _ (clojure.pprint/pprint  abstractions)]

    ;; Validate and transform abstractions
    (->> abstractions
         (mapv (fn [abstraction]

                 (let [validated-indices (->> (:file_indices abstraction)
                                              (filter (fn [idx]
                                                        (and (>= idx 0) (< idx file-count))))
                                              distinct
                                              sort
                                              vec)]
                   {:name (:name abstraction)
                    :description (:description abstraction)
                    :files validated-indices}))))))

(defn fetch-identified-abstractions!
  "process that identifies abstractions from crawled files, using the llm"
  ([] {:ins {:files "Map of file paths to contents"}
       :outs {:abstractions "List of identified abstractions"}})

  ;; init
  ([args] args)

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
   (l/log "Identifying abstractions" msg)
   (if (and (= in :files) (:ready state))

     (let [context-map (->llm-context msg)
           abstractions (identify-abstractions! context-map
                                                (:project-name msg)
                                                (:language msg))]
       [state {:abstractions [(assoc msg :abstractions abstractions)]}])
     [state nil])))





