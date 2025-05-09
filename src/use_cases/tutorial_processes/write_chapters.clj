(ns use-cases.tutorial-processes.write-chapters
  (:require 
   [clojure.core.async.flow :as flow]
   [clojure.string :as str]
   [adapters.gemini.llm :refer [stream-llm]]
   [adapters.logging.log :as l]))

(defn- create-safe-filename
  "Create a safe filename from a chapter name"
  [chapter-num chapter-name]
  (let [safe-name (-> chapter-name
                      (str/lower-case)
                      (str/replace #"[^a-z0-9]+" "_"))]
    (format "%02d_%s.md" chapter-num safe-name)))

(defn- get-chapter-filenames
  "Create a map of chapter filenames for all abstractions"
  [abstractions chapter-order]
  (into {}
        (map-indexed
         (fn [i abstraction-idx]
           (let [chapter-num (inc i)
                 chapter-name (get-in abstractions [abstraction-idx :name])
                 filename (create-safe-filename chapter-num chapter-name)]
             [abstraction-idx 
              {:num chapter-num
               :name chapter-name
               :filename filename}]))
         chapter-order)))

(defn- get-related-file-content
  "Get content from related files for an abstraction"
  [abstraction files file-info]
  (let [related-indices (:files abstraction)]
    (str/join "\n\n"
              (keep (fn [idx]
                      (when (< idx (count file-info))
                        (let [[file-idx file-path] (nth file-info idx)
                              file-content (get files file-path)]
                          (when file-content
                            (format "--- File: %s ---\n%s" 
                                    file-path file-content)))))
                    related-indices))))

(defn write-single-chapter!
  "Write a single chapter for the given abstraction index."
  [chapter-num abstraction file-content project-name chapter-listing 
   chapter-filenames prev-chapter next-chapter previous-chapters language]
  (let [language (or language "english")
        abstraction-name (:name abstraction)
        abstraction-description (:description abstraction)
        
        ;; Create summary of previous chapters
        previous-chapters-summary 
        (if (seq previous-chapters)
          (str/join "\n---\n" previous-chapters)
          "This is the first chapter.")
        
        ;; Language-specific variables
        lang-cap (str/capitalize language)
        is-english (= (str/lower-case language) "english")
        
        ;; Language variables
        language-instruction (if is-english "" 
                              (str "IMPORTANT: Write this ENTIRE tutorial chapter in **" 
                                   lang-cap 
                                   "**. Some input context (like concept name, description, chapter list, previous summary) might already be in " 
                                   lang-cap 
                                   ", but you MUST translate ALL other generated content including explanations, examples, technical terms, and potentially code comments into " 
                                   lang-cap 
                                   ". DO NOT use English anywhere except in code syntax, required proper nouns, or when specified. The entire output MUST be in " 
                                   lang-cap ".\n\n"))
        
        concept-details-note (if is-english "" (str " (Note: Provided in " lang-cap ")"))
        structure-note (if is-english "" (str " (Note: Chapter names might be in " lang-cap ")"))
        prev-summary-note (if is-english "" (str " (Note: This summary might be in " lang-cap ")"))
        instruction-lang-note (if is-english "" (str " (in " lang-cap ")"))
        mermaid-lang-note (if is-english "" (str " (Use " lang-cap " for labels/text if appropriate)"))
        code-comment-note (if is-english "" (str " (Translate to " lang-cap " if possible, otherwise keep minimal English for clarity)"))
        link-lang-note (if is-english "" (str " (Use the " lang-cap " chapter title from the structure above)"))
        tone-note (if is-english "" (str " (appropriate for " lang-cap " readers)"))
        
        ;; Build prompt section by section
        intro (str language-instruction 
                   "Write a very beginner-friendly tutorial chapter (in Markdown format) for the project `" 
                   project-name "` about the concept: \"" abstraction-name "\". This is Chapter " 
                   chapter-num ".\n\n")
        
        concept (str "Concept Details" concept-details-note ":\n- Name: " 
                    abstraction-name "\n- Description:\n" abstraction-description "\n\n")
        
        structure (str "Complete Tutorial Structure" structure-note ":\n" 
                      chapter-listing "\n\n")
        
        context (str "Context from previous chapters" prev-summary-note ":\n" 
                    previous-chapters-summary "\n\n")
        
        code-snippets (str "Relevant Code Snippets (Code itself remains unchanged):\n" 
                          (if (str/blank? file-content) 
                            "No specific code snippets provided for this abstraction." 
                            file-content) "\n\n")
        
        instructions (str "Instructions for the chapter (Generate content in " lang-cap " unless specified otherwise):\n"
                         "- Start with a clear heading (e.g., `# Chapter " chapter-num ": " abstraction-name "`). Use the provided concept name.\n\n"
                         "- If this is not the first chapter, begin with a brief transition from the previous chapter" instruction-lang-note ", referencing it with a proper Markdown link using its name" link-lang-note ".\n\n"
                         "- Begin with a high-level motivation explaining what problem this abstraction solves" instruction-lang-note ". Start with a central use case as a concrete example. The whole chapter should guide the reader to understand how to solve this use case. Make it very minimal and friendly to beginners.\n\n"
                         "- If the abstraction is complex, break it down into key concepts. Explain each concept one-by-one in a very beginner-friendly way" instruction-lang-note ".\n\n"
                         "- Explain how to use this abstraction to solve the use case" instruction-lang-note ". Give example inputs and outputs for code snippets (if the output isn't values, describe at a high level what will happen" instruction-lang-note ").\n\n"
                         "- Each code block should be BELOW 20 lines! If longer code blocks are needed, break them down into smaller pieces and walk through them one-by-one. Aggresively simplify the code to make it minimal. Use comments" code-comment-note " to skip non-important implementation details. Each code block should have a beginner friendly explanation right after it" instruction-lang-note ".\n\n"
                         "- Describe the internal implementation to help understand what's under the hood" instruction-lang-note ". First provide a non-code or code-light walkthrough on what happens step-by-step when the abstraction is called" instruction-lang-note ". It's recommended to use a simple sequenceDiagram with a dummy example - keep it minimal with at most 5 participants to ensure clarity. If participant name has space, use: `participant QP as Query Processing`. " mermaid-lang-note ".\n\n"
                         "- Then dive deeper into code for the internal implementation with references to files. Provide example code blocks, but make them similarly simple and beginner-friendly. Explain" instruction-lang-note ".\n\n"
                         "- IMPORTANT: When you need to refer to other core abstractions covered in other chapters, ALWAYS use proper Markdown links like this: [Chapter Title](filename.md). Use the Complete Tutorial Structure above to find the correct filename and the chapter title" link-lang-note ". Translate the surrounding text.\n\n"
                         "- Use mermaid diagrams to illustrate complex concepts (```mermaid``` format). " mermaid-lang-note ".\n\n"
                         "- Heavily use analogies and examples throughout" instruction-lang-note " to help beginners understand.\n\n"
                         "- End the chapter with a brief conclusion that summarizes what was learned" instruction-lang-note " and provides a transition to the next chapter" instruction-lang-note ". If there is a next chapter, use a proper Markdown link: [Next Chapter Title](next_chapter_filename)" link-lang-note ".\n\n"
                         "- Ensure the tone is welcoming and easy for a newcomer to understand" tone-note ".\n\n"
                         "- Output *only* the Markdown content for this chapter.\n\n"
                         "Now, directly provide a super beginner-friendly Markdown output (DON'T need ```markdown``` tags):")
        
        ;; Combine all parts to make the final prompt
        prompt (str intro concept structure context code-snippets instructions)
        
        ;; Call LLM and extract response
        _ (l/log (format "Writing chapter %d for: %s..." chapter-num abstraction-name))
        chapter-content (stream-llm
                         prompt
                         (fn [chunk]
                           (l/log chunk)
                           (flush)))
        
        ;; Basic validation/cleanup
        expected-heading (format "# Chapter %d: %s" chapter-num abstraction-name)
        
        ;; Ensure proper heading
        chapter-content 
        (if (str/starts-with? (str/trim chapter-content) (format "# Chapter %d" chapter-num))
          chapter-content
          (let [lines (str/split-lines (str/trim chapter-content))]
            (if (and (seq lines) (str/starts-with? (first lines) "#"))
              ;; Replace existing heading
              (str/join "\n" (cons expected-heading (rest lines)))
              ;; Add heading at the beginning
              (str expected-heading "\n\n" chapter-content))))]
    
    ;; Return the chapter content
    chapter-content))

(defn write-chapters!
  "Write tutorial chapters for all abstractions in the given order."
  [{:keys [chapter-order abstractions files file-info project-name language]}]
  (let [language (or language "english")
        
        ;; Create chapter filenames map
        chapter-filenames (get-chapter-filenames abstractions chapter-order)
        
        ;; Create full chapter listing
        chapter-listing
        (str/join "\n"
                  (map-indexed
                   (fn [i abstraction-idx]
                     (let [chapter-num (inc i)
                           chapter-info (get chapter-filenames abstraction-idx)
                           chapter-name (:name chapter-info)
                           filename (:filename chapter-info)]
                       (format "%d. [%s](%s)" chapter-num chapter-name filename)))
                   chapter-order))
        
        ;; Write chapters iteratively, keeping track of previously written ones
        chapters 
        (loop [indices chapter-order
               chapter-num 1
               result []
               previous-chapters []]
          (if (empty? indices)
            result
            (let [abstraction-idx (first indices)
                  abstraction (get abstractions abstraction-idx)
                  
                  ;; Get file content related to this abstraction
                  file-content (get-related-file-content abstraction files file-info)
                  
                  ;; Get previous and next chapter info
                  prev-idx (when (> chapter-num 1) 
                             (get chapter-order (- chapter-num 2)))
                  prev-chapter (when prev-idx 
                                 (get chapter-filenames prev-idx))
                  
                  next-idx (when (< chapter-num (count chapter-order)) 
                             (get chapter-order chapter-num))
                  next-chapter (when next-idx 
                                 (get chapter-filenames next-idx))
                  
                  ;; Write this chapter
                  chapter-content 
                  (write-single-chapter! 
                   chapter-num abstraction file-content project-name
                   chapter-listing chapter-filenames prev-chapter next-chapter
                   previous-chapters language)
                  
                  ;; Add to results and continue
                  new-result (conj result chapter-content)
                  new-previous (conj previous-chapters chapter-content)]
              
              (recur (rest indices) (inc chapter-num) new-result new-previous))))]
    
    chapters))

(defn generate-the-chapters!
  "Process that writes tutorial chapters based on abstractions and their order"
  ([] {:ins {:chapter-order "Information with chapter ordering"}
       :outs {:chapters "Generated chapter content"}})

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
   (if (and (= in :chapter-order) (:ready state))
     [state {:chapters [(assoc msg :chapters (write-chapters! (select-keys msg [:abstractions :files :file-info :language :project-name :chapter-order])))]}]
     [state nil])))