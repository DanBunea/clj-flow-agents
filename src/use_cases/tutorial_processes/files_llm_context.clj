(ns use-cases.tutorial-processes.files-llm-context
  (:require [clojure.string :as str]
            [adapters.filesystem.files :refer [crawl-local-files]]))

(defn ->llm-context
  "Convert files data from crawl-local-files into a context string for LLM processing.
   
   Args:
     files - Map of {filepath content} from crawl-local-files
   
   Returns:
     {:context string
      :file-info vector of [index path]}"
  [{:keys [files]}]
  (let [files-vec (vec (map-indexed vector (seq files)))
        context (str/join "\n\n"
                          (map (fn [[idx [path content]]]
                                 (format "--- File Index %d: %s ---\n%s"
                                         idx
                                         path
                                         content))
                               files-vec))
        file-info (map (fn [[idx [path _]]]
                         [idx path])
                       files-vec)]
    {:context context
     :file-info file-info}))

;; Example usage
(comment
  (def files-data (crawl-local-files "."
                                     :include-patterns #{"*.clj" "*.edn"}
                                     :exclude-patterns #{"*.pyc" "__pycache__/*" ".git/*" "output/*"}))
  (->llm-context files-data)

  )

