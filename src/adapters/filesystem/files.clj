(ns adapters.filesystem.files
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [adapters.logging.log :as log]))

(defn matches-pattern? [path patterns]
  (when patterns
    (some #(re-matches (re-pattern (str/replace % "*" ".*")) path) patterns)))

(defn normalize-path [path]
  "Normalize path separators to forward slashes"
  (str/replace path "\\" "/"))

(defn get-relative-path [base-dir file]
  "Get the relative path of a file from a base directory"
  (let [base-path (normalize-path (.getCanonicalPath base-dir))
        file-path (normalize-path (.getCanonicalPath file))]
    (if (.startsWith file-path base-path)
      (let [relative-path (subs file-path (min (inc (count base-path)) (count file-path)))]
        (if (empty? relative-path)
          (str/replace file-path base-path "")
          relative-path))
      file-path)))

(defn process-file [file base-dir {:keys [include-patterns exclude-patterns max-file-size use-relative-paths]}]
  "Process a single file and return its content if it matches criteria"
  (let [filepath (.getPath file)
        relpath (if use-relative-paths
                 (get-relative-path base-dir file)
                 filepath)]
    (when (and (.isFile file)
               (or (nil? include-patterns)
                   (matches-pattern? relpath include-patterns))
               (or (nil? exclude-patterns)
                   (not (matches-pattern? relpath exclude-patterns)))
               (or (nil? max-file-size)
                   (<= (.length file) max-file-size)))
      (try
        (let [content (slurp file :encoding "UTF-8")]
          [relpath content])
        (catch Exception e
          (println "Warning: Could not read file" filepath ":" (.getMessage e))
          nil)))))

(defn crawl-local-files
  "Crawl files in a local directory with similar interface as crawl_github_files.
   
   Args:
     directory - Path to local directory
     include-patterns - File patterns to include (e.g. #{\"*.py\" \"*.js\"})
     exclude-patterns - File patterns to exclude (e.g. #{\"tests/*\"})
     max-file-size - Maximum file size in bytes
     use-relative-paths - Whether to use paths relative to directory
   
   Returns:
     {:files {filepath content}}"
  [directory & {:keys [include-patterns exclude-patterns max-file-size use-relative-paths]
                :or {use-relative-paths true}}]
  (let [dir (io/file directory)]
    (when-not (.isDirectory dir)
      (throw (IllegalArgumentException. (str "Directory does not exist: " directory))))
    (log/log "crawling files...")
    (let [opts {:include-patterns include-patterns
                :exclude-patterns exclude-patterns
                :max-file-size max-file-size
                :use-relative-paths use-relative-paths}]
      (loop [files {}
             remaining (file-seq dir)]
        (if (empty? remaining)
          {:files files}
          (let [file (first remaining)
                result (process-file file dir opts)]
            (recur (if result
                    (assoc files (first result) (second result))
                    files)
                  (rest remaining))))))))

;; Example usage
(comment
  (println "--- Crawling parent directory ('..') ---")
  (let [files-data (crawl-local-files "."
                                      :include-patterns #{"*.clj", "*.edn"}
                                     :exclude-patterns #{"*.pyc" "__pycache__/*" ".git/*" "output/*"})]
    (println "Found" (count (:files files-data)) "files:")
    (doseq [path (keys (:files files-data))]
      (println "  " path)))
  
  (crawl-local-files "."
                     :include-patterns #{"*.clj", "*.edn"}
                     :exclude-patterns #{"*.pyc" "__pycache__/*" ".git/*" "output/*"})
  )

