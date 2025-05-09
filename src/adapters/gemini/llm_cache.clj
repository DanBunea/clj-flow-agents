(ns adapters.gemini.llm-cache
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [adapters.logging.log :as l]))

(def cache-file "llm_cache.json")

(defn load-cache
  "Load the cache from disk. Returns empty map if cache doesn't exist."
  []
  (try
    (if (.exists (io/file cache-file))
      (json/read-str (slurp cache-file))
      {})
    (catch Exception e
      (l/log "Failed to load cache:" (.getMessage e))
      {})))

(defn save-cache
  "Save the cache to disk."
  [cache]
  (try
    (spit cache-file (json/write-str cache))
    (catch Exception e
      (l/log "Failed to save cache:" (.getMessage e)))))

(defn get-cached
  "Get a cached response for a prompt if it exists."
  [prompt]
  (get (load-cache) prompt))

(defn cache-response!
  "Cache a response for a prompt."
  [prompt response]
  (let [cache (load-cache)]
    (save-cache (assoc cache prompt response)))) 