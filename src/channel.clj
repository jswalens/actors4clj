(ns channel)

(defprotocol Channel
  (put [this msg])
  (take [this]))

(defn channel []
  (let [msgs (atom [])]
    (reify Channel
      (put [this msg]
        (locking this
          (swap! msgs conj msg)
          (.notifyAll this)))
      (take [this]
        (locking this
          (loop []
            (when (empty? @msgs)
              (try
                (.wait this)
                (catch InterruptedException e
                  nil)) ; recur below
              (recur)))
          (let [msg (first @msgs)]
            (swap! msgs rest)
            ; doing this in two steps is ok as we're locking `this` so it is
            ; atomic anyway
            msg))))))
