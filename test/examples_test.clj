(ns examples-test
  (:require [clojure.test :refer :all]
            [log :refer [log]]
            [actor]))

(deftest counter
  (testing "COUNTER"
    (let [counter
            (actor/behavior [i]
              [:get]
                (log "count:" i)
              [:inc]
                (do
                  (log "count:" i "+ 1")
                  (become :self [(+ i 1)]))
              [:add j]
                (do
                  (log "count:" i "+" j)
                  (become :self [(+ i j)]))
              [:test n]
                (is (= i n)))
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
    (actor/send counter1 :test 10)
    (actor/send counter2 :test 10))))
