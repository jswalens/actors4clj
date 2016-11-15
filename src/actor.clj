(ns actor
  (:refer-clojure :exclude [send])
  (:require [clojure.core.async :as async :refer
              [chan go go-loop >! <! >!! <!! put! close! mult tap]]
            [clojure.core.match :refer [match]]
            [stm]
            [log :refer [log]]))

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
              (stm/ref [nil nil])
            ~'become
              (fn [behavior# state#]
                (let [behavior_# (if (= behavior# :self) nil behavior#)]
                  (stm/dosync
                    ; If we're already in a transaction, this becomes part of
                    ; the outer transaction; otherwise this simply sets the new
                    ; behavior and state.
                    (stm/ref-set new-behavior-and-state# [behavior_# state#]))))]
            ; inject become
        (match [msg#]
          ~@match-clauses
          :else (println "error: message" msg# "does not match any pattern"))
        (stm/deref new-behavior-and-state#)))))

(defmacro spawn [initial-behavior state]
  `(let [inbox# (chan)]
    (go
      (loop [behavior# ~initial-behavior
             state#    ~state]
        (let [msg# (<! inbox#)
              ;_#   (log "| received" msg#)
              [new-behavior# new-state#] (behavior# msg# state#)]
          (recur (or new-behavior# behavior#) (or new-state# state#)))))
    inbox#))

(defn send [actor & args]
  (put! actor args))
