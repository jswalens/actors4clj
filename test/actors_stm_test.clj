(ns actors-stm-test
  (:require [clojure.test :refer :all]
            [log :refer [log]]
            [actor]
            [stm]))

; COUNTER: uses transactions
(deftest counter
  (testing "COUNTER - WORKS AS EXPECTED"
    (let [sum (stm/ref 0)
          counter
            (actor/behavior [i]
              [:get]
                (stm/dosync
                  (log "my sum:" i "- total sum:" (stm/deref sum)))
              [:inc]
                (stm/dosync
                  (log "my sum:" i "+ 1 - total sum:" (stm/deref sum) "+ 1")
                  (stm/alter sum + 1)
                  (become :self [(+ i 1)]))
              [:add j]
                (stm/dosync
                  (log "my sum:" i "+" j "- total sum:" (stm/deref sum) "+" j)
                  (stm/alter sum + j)
                  (become :self [(+ i j)])))
          counter1 (actor/spawn counter [0])
          counter2 (actor/spawn counter [0])]
    (actor/send counter1 :inc)
    (actor/send counter2 :inc)
    (actor/send counter1 :add 5)
    (actor/send counter1 :get)
    (actor/send counter1 :add 4)
    (actor/send counter2 :add 9)
    (actor/send counter2 :get)
    (actor/send counter1 :get)
    (Thread/sleep 100)
    (actor/send counter1 :get) (Thread/sleep 10)
    (actor/send counter2 :get) (Thread/sleep 10)
    (is (= 20 (stm/deref sum)))))) ; this succeeds

; SUMMER: send in transaction
(deftest summer-send
  (testing "SUMMER - PROBLEM WITH SEND"
    (let [contentious-ref (stm/ref 0)
          receiver
            (actor/behavior [i]
              [:get]
                (log "received" i "messages")
              [:check]
                (is (= 100 i))
              [:inc]
                (become :self [(inc i)]))
          receiver-actor (actor/spawn receiver [0])
          sender
            (actor/behavior []
              [:do]
                (stm/dosync
                  (actor/send receiver-actor :inc)
                  (stm/alter contentious-ref inc)))
          senders (doall (repeatedly 100 #(actor/spawn sender [])))]
      (is (= 100 (count senders)))
      (doseq [s senders]
        (actor/send s :do))
      (Thread/sleep 1000)
      (actor/send receiver-actor :get) (Thread/sleep 10)
      (log "expected 100 messages received")
      (actor/send receiver-actor :check) (Thread/sleep 10)))) ; this fails
      ; Since send is not reverted when a transaction is reverted, its effects
      ; remain visible after a rollback.

; SUMMER: spawn in transaction
(deftest summer-spawn
  (testing "SUMMER - PROBLEM WITH SPAWN"
    (let [sum (stm/ref 0)
          contentious-ref (stm/ref 0)
          counter
            (actor/behavior [i]
              [:get]
                (stm/dosync
                  (log "my sum:" i "- total sum:" (stm/deref sum)))
              [:inc]
                (stm/dosync
                  (log "my sum:" i "+ 1 - total sum:" (stm/deref sum) "+ 1")
                  (stm/alter sum + 1)
                  (become :self [(+ i 1)]))
              [:add j]
                (stm/dosync
                  (log "my sum:" i "+" j "- total sum:" (stm/deref sum) "+" j)
                  (stm/alter sum + j)
                  (become :self [(+ i j)])))
          spawner
            (actor/behavior []
              [:do]
                (stm/dosync
                  (actor/send (actor/spawn counter [0]) :inc)
                  (stm/alter contentious-ref inc)))
          spawners (doall (repeatedly 100 #(actor/spawn spawner [])))]
      (is (= 100 (count spawners)))
      (doseq [s spawners]
        (actor/send s :do))
      (Thread/sleep 1000)
      (is (= 100 (stm/deref sum)))))) ; this fails
      ; Since spawn is not reverted when a transaction is reverted, its effects
      ; remain visible after a rollback.

; FLAGGER: become in transaction
(deftest flagger-become
  (testing "FLAGGER - PROBLEM WITH BECOME"
    (let [one-flag-set? (stm/ref false)
          flags-at-the-end (agent [])
          flagger
            (actor/behavior [flag]
              [:set-flag]
                (stm/dosync
                  (when-not (stm/deref one-flag-set?)
                    (become :self [true])
                    (stm/ref-set one-flag-set? true)))
                  ; else: flag stays false, one-flag-set? stays true
              [:read-flag]
                (send flags-at-the-end conj flag)) ; NOT actor/send but to agent
          flaggers (doall (repeatedly 100 #(actor/spawn flagger [false])))]
      (is (= 100 (count flaggers)))
      (doseq [f flaggers]
        (actor/send f :set-flag))
      (Thread/sleep 1000)
      (doseq [f flaggers]
        (actor/send f :read-flag))
      (Thread/sleep 1000)
      (log "flags at the end:" @flags-at-the-end)
      (let [n (count @flags-at-the-end)
            t (count (filter true? @flags-at-the-end))
            f (count (filter false? @flags-at-the-end))]
        (log "true:" t "/" n "- false:" f "/" n)
        (is (= 1 t))
        (is (= (- n 1) f)))))) ; this fails
      ; Since become uses an atom, it is not reverted automatically when the
      ; transaction is undone.
      ; Possible solution: use ref for become.
