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

(deftest self-send
  (testing "SELF SEND"
    (let [cell (actor/behavior [v]
                               [:get]
                               (become :self [v])
                               [:put j]
                               (become :self [j])
                               [:send-self]
                               (do (actor/send actor/self :put :my_value)
                                   (become :self [v]))
                               [:test]
                               (do (is (= v :my_value))
                                   (become :self [v])))
          cellactor1 (actor/spawn cell nil)]
      (actor/send cellactor1 :put :first_value)
      (actor/send cellactor1 :send-self)
      (Thread/sleep 500)
      (actor/send cellactor1 :test))))


(deftest self-send2
  (testing "SELF SEND SCOPE"
    (let [cell (actor/behavior [v]
                               [:get]
                               (become :self [v])
                               [:put j]
                               (become :self [j])
                               [:send-self v']
                               (do (actor/send actor/self :put v')
                                   (become :self [v]))
                               [:test test-value]
                               (do (is (= v test-value))
                                   (become :self [v])))
          cellactor1 (actor/spawn cell nil)
          cellactor2 (actor/spawn cell nil)]

      ;; Send an initial value to the actors and verify.
      (actor/send cellactor1 :put :first_value)
      (actor/send cellactor2 :put :first_value)
      (actor/send cellactor1 :test :first_value)
      (actor/send cellactor2 :test :first_value)
      ;; Send a unique value to each actor and verify
      (actor/send cellactor1 :send-self :sent_to_self_1)
      (actor/send cellactor2 :send-self :sent_to_self_2)
      (Thread/sleep 500)
      (actor/send cellactor1 :test :sent_to_self_1)
      (actor/send cellactor2 :test :sent_to_self_2)
      (Thread/sleep 1000))))


(deftest self-send3
  (testing "ECHO"
    (let [echo   (actor/behavior []
                                 [:echo v from]
                                 (do (actor/send from v from)
                                     (become :self [])))
          client (actor/behavior [i]
                                 [:start from]
                                 (do (actor/send from :echo :echo_this actor/self)
                                     (become :self [(inc i)]))
                                 [:echo_this should_be_self]
                                 (do (is (= should_be_self actor/self))
                                     (become :self []))
                                  [:test]
                                  (do (is (= 1 i)
                                      (become :self []))))
          echoactor   (actor/spawn echo [])
          clientactor (actor/spawn client [0])]
      ;; Make the client send an :echo message to the echo actor.
      ;; The echo actor will send back the reference it got, so the client can
      ;; verify that it is indeed an echo.
      (actor/send clientactor :start echoactor)
      ;; Verify the reply was received.
      (actor/send clientactor :test))))
