(ns log)

(def logger (agent nil))

(defn log [& args]
  nil)

; (defn log [& args]
;   (send logger (fn [_] (apply println args))))
