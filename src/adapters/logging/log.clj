(ns adapters.logging.log)

(defn truncate-string
  "If string is longer than max-length, show first and last (max-length/2) characters with ... in between"
  [max-length s]
  (if (and (string? s) (> (count s) (* 2 max-length)))
    (str (subs s 0 max-length) "...\n...\n..." (subs s (- (count s) max-length)))
    s))

(defn log [& args]
  (let [time (.format (java.time.LocalTime/now)
                      (java.time.format.DateTimeFormatter/ofPattern "hh:mm:ss:SS"))
        truncated-args (map (partial truncate-string 100) args)]
    (apply println (cons (str "[" time "] ") truncated-args))))

(comment

  (log 1 "The number of letters in the pattern format is significant.
If we use a two-letter pattern for the month, we’ll get a two-digit month representation. If the month number is less than 10, it will be padded with a zero. When we don’t need the mentioned padding with zeroes, we can use a one-letter pattern “M,” which will show January as “1.”
If we happen to use a four-letter pattern for the month, “MMMM,” then we’ll get a “full form” representation. In our example, it would be “July.” A five-letter pattern, “MMMMM,” will make the formatter use the “narrow form.” In our case, “J” would be used.
Likewise, custom formatting patterns can also be used to parse a String that holds a date:")

  nil
  )