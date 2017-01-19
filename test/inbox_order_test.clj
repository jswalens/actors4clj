(ns inbox-order-test
  (:require [clojure.test :refer :all]
            [log :refer [log]]
            [actor]))

(deftest order-respected-direct
  "Test whether messages are received in the same order they were sent."
  (let [a (actor/behavior [b]
            [:do]
              (do
                (actor/send b :set 1)
                (actor/send b :set 2)
                (actor/send b :set 3)
                (actor/send b :set 4)))
        b (actor/behavior [state]
            [:set i]
              (do
                (is (= state (- i 1)))
                (become :self [i]))
            [:get promise]
              (deliver promise state))
        bs (doall (repeatedly 100 #(actor/spawn b [0])))
        as (doall (map #(actor/spawn a [%]) bs))]
    (doseq [a as]
      (actor/send a :do))
    (Thread/sleep 700)
    (doseq [b bs]
      (let [p (promise)]
        (actor/send b :get p)
        (is (= 4 @p))))))

(deftest order-respected-indirect
  "Test whether messages cannot get interleaved in an incorrect way.
  A sends to C, then to B. B forwards to C. C should first receive the message
  from A and only then the one from B.
  In this case there are no transactions at all so it should always work
  correctly. Hence, it tests the order in which messages are taken out of the
  inbox is correct."
  (let [a (actor/behavior [b c]
            [:do]
              (do
                (actor/send c :from-a)
                (actor/send b :do)))
        b (actor/behavior [c]
            [:do]
              (actor/send c :from-b))
        c (actor/behavior [state]
            [:from-a]
              (do
                (is (= state :initial))
                (become :self [:after-a]))
            [:from-b]
              (do
                (is (= state :after-a))
                (become :self [:after-b]))
            [:get promise]
              (deliver promise state))
        cs (doall (repeatedly 100 #(actor/spawn c [:initial])))
        bs (doall (map #(actor/spawn b [%]) cs))
        as (doall (map (fn [[b c]] (actor/spawn a [b c]))
                    (partition 2 (interleave bs cs))))]
    (doseq [a as]
      (actor/send a :do))
    (Thread/sleep 500)
    (doseq [c cs]
      (let [p (promise)]
        (actor/send c :get p)
        (is (= :after-b @p))))))
