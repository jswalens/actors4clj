(use '[leiningen.exec :only (deps)])

(deps '[[org.clojure/clojure "1.6.0"]
        [org.clojure/core.async "0.2.391"]
        [org.clojure/core.match "0.2.2"]])

(require '[clojure.core.async :as async :refer
  [chan go go-loop >! <! >!! <!! put! close! mult tap]])

(require '[clojure.core.match :refer [match]])


; ******************************************************************************
; * TESTS                                                                      *
; ******************************************************************************

; COUNTER - WORKS
(println "COUNTER - WORKS AS EXPECTED")
(def sum (ref 0))

(def counter
  (behavior [i]
    [:get]
      (atomic
        (println "my sum:" i "- total sum:" @sum))
    [:inc]
      (atomic
        (println "my sum:" i "+ 1 - total sum:" @sum "+ 1")
        (alter sum + 1)
        (become counter [(+ i 1)]))
    [:add j]
      (atomic
        (println "my sum:" i "+" j "- total sum:" @sum "+" j)
        (alter sum + j)
        (become counter [(+ i j)]))))


(def counter1 (spawn_ counter [0]))
(def counter2 (spawn_ counter [0]))
(send_ counter1 :inc)
(send_ counter2 :inc)
(send_ counter1 :add 5)
(send_ counter1 :get)
(send_ counter1 :add 4)
(send_ counter2 :add 9)
(send_ counter2 :get)
(send_ counter1 :get)
(Thread/sleep 100)
(send_ counter1 :get) (Thread/sleep 10)
(send_ counter2 :get) (Thread/sleep 10)
(println "total sum:" @sum "- expected:" 20) ; this is correct
(println)


; SUMMER - PROBLEM WITH SEND
(println "SUMMER - PROBLEM WITH SEND")
(def contentious-ref (ref 0))

(def receiver
  (behavior [i]
    [:get]
      (println "received" i "messages")
    [:inc]
      (become receiver [(inc i)])))

(def receiver-actor (spawn_ receiver [0]))

(def sender
  (behavior []
    [:do]
      (dosync
        (send_ receiver-actor :inc)
        (alter contentious-ref inc))))

(def senders (doall (repeatedly 100 #(spawn_ sender []))))
(println "n of senders:" (count senders))
(doseq [s senders] (send_ s :do))
(Thread/sleep 1000)
(send_ receiver-actor :get) (Thread/sleep 10)
(println "expected 100 messages received") ; this is not correct
; Since send is not reverted when a transaction is reverted, its effects remain
; visible after a rollback.
(println)


; SUMMER - PROBLEM WITH SPAWN
(println "SUMMER - PROBLEM WITH SPAWN")
(dosync
  (ref-set sum 0))

(def spawner
  (behavior []
    [:do]
      (dosync
        (send_ (spawn_ counter [0]) :inc)
        (alter contentious-ref inc))))

(def spawners (doall (repeatedly 100 #(spawn_ spawner []))))
(println "n of spawners:" (count spawners))
(doseq [s spawners] (send_ s :do))
(Thread/sleep 1000)
(println "sum of all counters:" @sum "- expected 100") ; this is not correct
; Since spawn is not reverted when a transaction is reverted, its effects remain
; visible after a rollback.
(println)


; FLAGGER - PROBLEM WITH BECOME
(println "FLAGGER - PROBLEM WITH BECOME")
(def one-flag-set? (ref false))
(def flags-at-the-end (agent []))

(def flagger
  (behavior [flag]
    [:set-flag]
      (dosync
        (when-not @one-flag-set?
          (become flagger [true])
          (ref-set one-flag-set? true)))
        ; else: flag stays false, one-flag-set? stays true
    [:read-flag]
      (send flags-at-the-end conj flag)))

(def flaggers (doall (repeatedly 100 #(spawn_ flagger [false]))))
(println "n of flaggers:" (count flaggers))
(doseq [f flaggers] (send_ f :set-flag))
(Thread/sleep 1000)
(doseq [f flaggers] (send_ f :read-flag))
(Thread/sleep 1000)
(println "flags at the end:" @flags-at-the-end)
(let [n (count @flags-at-the-end)
      t (count (filter true? @flags-at-the-end))
      f (count (filter false? @flags-at-the-end))]
  (println "true:" t "/" n "- false:" f "/" n))
(println "only one true expected") ; this is not correct
; Since become uses an atom, it is not reverted automatically when the
; transaction is undone.
; Possible solution: use ref for become.

(Thread/sleep 1000)
(shutdown-agents)
