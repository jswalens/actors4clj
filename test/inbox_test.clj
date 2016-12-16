(ns inbox-test
  (:require [clojure.test :refer :all]
            [actor :refer [inbox inbox-put inbox-take]]))

(deftest send-receive
  (testing "send & receive"
    (let [in (inbox)
          a (future (inbox-put in :a))
          b (future (is (= :a (inbox-take in))))]
      @a
      @b)))

(deftest ping-pong
  (testing "ping pong"
    (let [in (inbox)
          a (future
              (is (= :ping (inbox-take in)))
              (inbox-put in :pong))
          b (future
              (inbox-put in :ping)
              @a ; wait for a to take :ping before b takes :pong
              (is (= :pong (inbox-take in))))]
      @a
      @b)))

(deftest hundred-ordered
  (testing "100 ordered messages"
    (let [in (inbox)]
      (dotimes [i 100]
        (inbox-put in i))
      (dotimes [i 100]
        (is (= i (inbox-take in)))))))

(deftest interleaved
  (testing "interleaved puts and takes"
    (let [in (inbox)]
      (doseq [i (range 0 100)]
        (inbox-put in i))
      (doseq [i (range 0 50)]
        (is (= i (inbox-take in))))
      (doseq [i (range 100 150)]
        (inbox-put in i))
      (doseq [i (range 50 150)]
        (is (= i (inbox-take in)))))))

(deftest hundred-receivers
  (testing "1 sender, 100 receivers"
    (let [in (inbox)
          sender
            (future
              (dotimes [i 100]
                (inbox-put in i)))
          receivers
            (doall (for [i (range 100)]
              (future
                (inbox-take in))))]
      (is (= 4950 (reduce + (map deref receivers)))))))

(deftest hundred-senders
  (testing "100 senders, 1 receiver"
    (let [in (inbox)
          senders
            (dotimes [i 100]
              (future
                (inbox-put in i)))
          receiver
            (future
              (doall (for [i (range 100)]
                (inbox-take in))))]
      (is (= 4950 (reduce + @receiver))))))
