(ns channel-test
  (:require [clojure.test :refer :all]
            [channel]))

(deftest send-receive
  (testing "send & receive"
    (let [c (channel/channel)
          a (future (channel/put c :a))
          b (future (is (= :a (channel/take c))))]
      @a
      @b)))

(deftest ping-pong
  (testing "ping pong"
    (let [c (channel/channel)
          a (future
              (is (= :ping (channel/take c)))
              (channel/put c :pong))
          b (future
              (channel/put c :ping)
              @a ; wait for a to take :ping before b takes :pong
              (is (= :pong (channel/take c))))]
      @a
      @b)))

(deftest hundred-ordered
  (testing "100 ordered messages"
    (let [c (channel/channel)]
      (dotimes [i 100]
        (channel/put c i))
      (dotimes [i 100]
        (is (= i (channel/take c)))))))
