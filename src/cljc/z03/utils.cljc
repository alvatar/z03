(ns z03.utils)

(def valid-chars
  (map char (concat (range 48 58) ; 0-9
                    (range 65 91) ; A-Z
                    (range 97 123)))) ; a-z

(defn rand-str [len]
  (apply str (take len (repeatedly #(rand-nth valid-chars)))))
