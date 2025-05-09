(ns use-cases.tutorial-processes.analyze-relationships
  (:require 
   [clojure.core.async.flow :as flow]
   [clojure.string :as str]
   [clojure.data.json :as json]
   [adapters.gemini.llm :refer [stream-llm]] 
   
   [adapters.logging.log :as l]))



(defn analyze-relationships!
  "Analyze relationships between the identified abstractions.
   
   Args:
     abstractions - Vector of abstraction maps with :name, :description, and :files keys
     files - Map of file paths to contents
     file-info - Map of indices to file paths
     project-name - String name of the project
     language - Optional string language code (defaults to 'english')
   
   Returns:
     Map with :summary string and :details vector of relationship maps"
  [{:keys [abstractions files file-info project-name language]} ]
  (let [language (or language "english")

        ;; Create abstraction-info and collect all relevant indices without atoms
        abstraction-info-for-prompt
        (map-indexed
         (fn [i abstr]
           (str i " # " (:name abstr)))
         abstractions)



        ;; Collect all file indices referenced by abstractions
        all-relevant-indices
        (-> (mapcat :files abstractions)
            distinct
            sort)

        ;; Build context for each abstraction
        abstractions-context
        (str "Identified Abstractions:\n"
             (str/join "\n"
                       (map-indexed
                        (fn [i abstr]
                          (let [file-indices-str (str/join ", " (:files abstr))]
                            (format "- Index %d: %s (Relevant file indices: [%s])\n  Description: %s"
                                    i (:name abstr) file-indices-str (:description abstr))))
                        abstractions)))

        ;; Generate file content snippets for relevant indices
        relevant-files-content
        (keep (fn [idx]
                (when (< idx (count file-info))
                  (let [[file-idx file-path] (nth file-info idx)
                        file-content (get files file-path)]
                    (when file-content
                      (format "--- File: %d # %s ---\n%s"
                              file-idx file-path file-content)))))
              all-relevant-indices)

        ;; Combine to form full context
        full-context
        (str abstractions-context
             "\n\nRelevant File Snippets (Referenced by Index and Path):\n"
             (str/join "\n\n" relevant-files-content))

        ;; Add language instruction and hints if not English
        language-instruction
        (when (not= (str/lower-case language) "english")
          (format "IMPORTANT: Generate the `summary` and relationship `label` fields in **%s** language. Do NOT use English for these fields.\n\n"
                  (str/capitalize language)))

        lang-hint
        (when (not= (str/lower-case language) "english")
          (format " (in %s)" (str/capitalize language)))

        list-lang-note
        (when (not= (str/lower-case language) "english")
          (format " (Names might be in %s)" (str/capitalize language)))



        ;; Create the prompt
        prompt (format "
Based on the following abstractions and relevant code snippets from the project `%s`:

List of Abstraction Indices and Names%s:
%s

Context (Abstractions, Descriptions, Code):
%s

%sPlease provide:
1. A high-level `summary` of the project's main purpose and functionality in a few beginner-friendly sentences%s. Use markdown formatting with **bold** and *italic* text to highlight important concepts.
2. A list (`relationships`) describing the key interactions between these abstractions. For each relationship, specify:
    - `from_abstraction`: Index of the source abstraction (e.g., `0 # AbstractionName1`)
    - `to_abstraction`: Index of the target abstraction (e.g., `1 # AbstractionName2`)
    - `label`: A brief label for the interaction **in just a few words**%s (e.g., \"Manages\", \"Inherits\", \"Uses\").
    Ideally the relationship should be backed by one abstraction calling or passing parameters to another.
    Simplify the relationship and exclude those non-important ones.

IMPORTANT: Make sure EVERY abstraction is involved in at least ONE relationship (either as source or target). Each abstraction index must appear at least once across all relationships.

Format the output as JSON:

```json
{
  \"summary\": \"A brief, simple explanation of the project%s. Can span multiple lines with **bold** and *italic* for emphasis.\",
  \"relationships\": [
    {
      \"from_abstraction\": \"0 # AbstractionName1\",
      \"to_abstraction\": \"1 # AbstractionName2\",
      \"label\": \"Manages\"%s
    },
    {
      \"from_abstraction\": \"2 # AbstractionName3\",
      \"to_abstraction\": \"0 # AbstractionName1\",
      \"label\": \"Provides config\"%s
    }
  ]
}
```

Now, provide the JSON output:"
                       project-name
                       list-lang-note
                       (str/join "\n" abstraction-info-for-prompt)
                       full-context
                       (or language-instruction "")
                       lang-hint
                       lang-hint
                       lang-hint
                       lang-hint
                       lang-hint)

        ;; Call LLM and extract response
        _ (l/log "Calling LLM for relationship analysis...")
        response (stream-llm
                  prompt
                  (fn [chunk]
                    (l/log chunk)
                    (flush)))
        _ (l/log "Got LLM response for relationships")

        ;; Extract JSON content
        json-str (-> response
                     (str/split #"```json")
                     second
                     (str/split #"```")
                     first
                     str/trim)

        ;; Parse JSON
        relationships-data (json/read-str json-str :key-fn keyword)
        _ (l/log "Parsed relationships data")

        ;; Validate relationships structure
        _ (when-not (and (map? relationships-data)
                         (:summary relationships-data)
                         (:relationships relationships-data))
            (throw (ex-info "LLM output is not a map or missing keys"
                            {:received relationships-data})))

        ;; Validate relationships (using keep instead of doseq + atom)
        num-abstractions (count abstractions)
        validated-relationships
        (keep (fn [rel]
                (try
                  (let [from-idx (-> (:from_abstraction rel)
                                     (str/split #"#")
                                     first
                                     str/trim
                                     Integer/parseInt)
                        to-idx (-> (:to_abstraction rel)
                                   (str/split #"#")
                                   first
                                   str/trim
                                   Integer/parseInt)]

                    (when (and (< -1 from-idx num-abstractions)
                               (< -1 to-idx num-abstractions))
                      {:from from-idx
                       :to to-idx
                       :label (:label rel)}))
                  (catch Exception e
                    (l/log "Warning: Could not parse relationship" rel)
                    nil)))
              (:relationships relationships-data))

        ;; Prepare the final structure
        result {:summary (:summary relationships-data)
                :details validated-relationships}]

    ;; Return validated relationships data
    (do
      (l/log "Returning" (count validated-relationships) "validated relationships")
      result)))

(defn generate-relationships!
  "Process that analyzes relationships between identified abstractions"
  ([] {
       :ins {:abstractions "Information about identified abstractions including the files data"}
       :outs {:relationships "List of relationships between abstractions"}})

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
   (if (and (= in :abstractions) (:ready state))
     [state {:relationships [(assoc msg :relationships (analyze-relationships! (select-keys msg [:abstractions :files :file-info :project-name :language])))]}]
     [state nil]))) 