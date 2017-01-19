(ns actor
  (:refer-clojure :exclude [send])
  (:require [clojure.core.match :refer [match]]
            [log :refer [log]])
  (:import [java.util.concurrent ConcurrentLinkedDeque]))

; ******************************************************************************
; * ACTORS INTERNALS                                                           *
; ******************************************************************************

(defn patterns->match-clauses [patterns]
  "Convert patterns as given in behavior definition into clauses as expected by
  clojure.core.match/match."
  (->> patterns
    ; ([:ping] ping [:pong] pong)
    (partition 2)
    ; (([:ping] ping) ([:pong] pong))
    (map (fn [[pattern action]] [[(list pattern :seq)] action]))
    ; ([[([:ping] :seq)] ping] [[([:pong] :seq)] pong])
    (apply concat))) ; flatten one level
    ; ([([:ping] :seq)] ping [([:pong] :seq)] pong)
    ; Therefore, splicing this results in:
    ; [([:ping] :seq)] ping
    ; [([:pong] :seq)] pong

(defprotocol Inbox
  (inbox-put [this msg])
  (inbox-take [this]))

(defn inbox []
  (let [msgs (ConcurrentLinkedDeque.)]
    (reify Inbox
      (inbox-put [this msg]
        (locking msgs
          (.add msgs msg)
          (.notifyAll msgs)))
      (inbox-take [this]
        (locking msgs
          (loop []
            (let [msg (.poll msgs)]
              (if (some? msg)
                msg
                (do
                  (try
                    (.wait msgs)
                    (catch InterruptedException e
                      nil)) ; recur below
                  (recur))))))))))


; ******************************************************************************
; * ACTORS PUBLIC API                                                          *
; ******************************************************************************

(defmacro behavior [variables & patterns]
  (let [match-clauses (patterns->match-clauses patterns)]
    `(fn [msg# state#]
      "Returns new behavior and state, or [nil nil] (or [:self nil]) if the same
      behavior and state can be kept."
      (let [~variables
              state#
            ; inject variables
            ; works automatically due to vector destructuring, e.g.
            ; with variables = [x y z] and state# = [1 2 3] this is
            ; (let [[x y z] [1 2 3]] ...)
            new-behavior-and-state#
              (atom [nil nil])
            ~'become
              (fn [behavior# state#]
                (let [behavior_# (if (= behavior# :self) nil behavior#)]
                  (reset! new-behavior-and-state# [behavior_# state#])))]
            ; inject become
        (match [msg#]
          ~@match-clauses
          :else (println "error: message" msg# "does not match any pattern"))
        @new-behavior-and-state#))))

(defmacro spawn [initial-behavior state]
  `(let [inbox# (inbox)]
    (future
      (loop [behavior# ~initial-behavior
             state#    ~state]
        (let [msg# (inbox-take inbox#)
              ;_#   (log "| received" msg#)
              [new-behavior# new-state#] (behavior# msg# state#)]
          (recur (or new-behavior# behavior#) (or new-state# state#)))))
    inbox#))

(defn send [actor & args]
  (inbox-put actor args))
