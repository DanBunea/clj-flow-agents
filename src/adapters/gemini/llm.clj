(ns adapters.gemini.llm
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [clj-http.client :as http]
            [adapters.gemini.llm-cache :as cache]
            [adapters.logging.log :as log]))

(defn load-config []
  (let [props (java.util.Properties.)]
    (.load props (io/input-stream (io/resource "config.properties")))
    props))

(def config (load-config))

(def api-key (or (System/getenv "GOOGLE_API_KEY") 
                 (.getProperty config "google.api.key")))
(def model-name (or (System/getenv "GEMINI_MODEL") 
                    (.getProperty config "gemini.model.name")))
(def stream-api-url (str "https://generativelanguage.googleapis.com/v1beta/models/" model-name ":streamGenerateContent"))



(defn process-line
  "Process a single line of SSE data from the Gemini API.
   Returns the extracted text if successful, nil otherwise."
  [line callback]
  (when-not (str/blank? line)
    (try
      (when (.startsWith line "data: ")
        (let [data (subs line 6)
              json (json/read-str data :key-fn keyword)
              text (-> json :candidates first :content :parts first :text)]
          #_(log "Extracted text:" text)
          #_(log/log text)
          (when text
            (callback text)
            text)))  ; Return the text to be accumulated
      (catch Exception e
        (log/log "Failed to process line:" line "Error:" (.getMessage e))
        nil))))

(defn process-stream
  "Process a stream of SSE data from the Gemini API.
   Calls the provided callback function for each chunk as it arrives.
   Returns the complete accumulated content at the end."
  [input-stream callback]
  (let [reader (java.io.BufferedReader. (java.io.InputStreamReader. input-stream))]
    (log/log "Created buffered reader for stream")
    (loop [accumulated ""]
      (if-let [line (.readLine reader)]
        (recur (str accumulated (or (process-line line callback) "")))
        accumulated))))

(defn make-stream-request
  "Make a streaming request to the Gemini API.
   Returns a tuple of [status response-body] on success.
   Throws ex-info with details on failure."
  [prompt]
  (log/log "Prompt:" prompt)
  (let [response (http/post stream-api-url
                           {:headers {"Content-Type" "application/json"
                                    "Accept" "text/event-stream"
                                    "x-goog-api-key" api-key}
                            :query-params {"alt" "sse"}  ; Request SSE format via query parameter
                            :body (json/write-str
                                  {:contents [{:parts [{:text prompt}]}]})
                            :as :stream
                            :async? false
                            :throw-exceptions false})
        status (:status response)]
    (log/log "Response status:" status)
    (if (= status 200)
      [status (:body response)]
      (let [error-body (slurp (:body response))]
        (log/log "API error response:" error-body)
        (throw (ex-info "API request failed" 
                       {:status status 
                        :body (try 
                               (json/read-str error-body :key-fn keyword)
                               (catch Exception _ error-body))}))))))

(defn stream-llm
  "Call the Google Generative AI API with a prompt and stream the responses.
   Takes a callback function that will be called with each chunk of text as it arrives.
   Returns the complete accumulated content at the end.
   Always uses caching to avoid duplicate API calls."
  [prompt callback]
  (log/log "Streaming prompt:" prompt)
  (if-let [cached-response (cache/get-cached prompt)]
    (do
      (log/log "Using cached response for prompt")
      (callback cached-response) ; Call callback with full cached response
      cached-response)
    ;; Make API call if not in cache
    (let [[_ response-body] (make-stream-request prompt)
          content (process-stream response-body callback)]
      ;; Update cache
      (cache/cache-response! prompt content)
      content)))

(comment


  (make-stream-request "Tell me another famous quote")


  ;; Example of using the streaming API with real-time output
  
    (stream-llm
     "Write a short poem about a small child learning to paint."
     (fn [chunk]
       (log/log chunk)
       #_(flush)))
    (println "\nStream completed.")
    
  )