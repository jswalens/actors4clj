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

(deftest hundred-receivers
  (testing "1 sender, 100 receivers"
    (let [c (channel/channel)
          sender
            (future
              (dotimes [i 100]
                (channel/put c i)))
          receivers
            (doall (for [i (range 100)]
              (future
                (channel/take c))))]
      (is (= 4950 (reduce + (map deref receivers)))))))

(deftest hundred-senders
  (testing "100 senders, 1 receiver"
    (let [c (channel/channel)
          senders
            (dotimes [i 100]
              (future
                (channel/put c i)))
          receiver
            (future
              (doall (for [i (range 100)]
                (channel/take c))))]
      (is (= 4950 (reduce + @receiver))))))
