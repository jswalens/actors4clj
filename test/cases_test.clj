(ns cases-test
  (:require [clojure.test :refer :all]
            [log :refer [log]]
            [actor]
            [stm]))

(deftest tx-to-non-tx
  "Send from transaction. Receiver contains no transaction."
  (let [n-send (stm/ref 0) ; contentious
        sender (actor/behavior [rcv]
                 [:do]
                   (stm/dosync
                     (actor/send rcv :inc)
                     (stm/alter n-send inc)))
        receiver (actor/behavior [i]
                   [:inc]
                     (become :self [(inc i)])
                   [:get promise]
                     (deliver promise i))
        receivers (doall (repeatedly 100 #(actor/spawn receiver [0])))
        senders   (doall (map #(actor/spawn sender [%]) receivers))]
    (doseq [s senders]
      (actor/send s :do))
    (Thread/sleep 500)
    (is (= 100 (stm/deref n-send)))
    (doseq [r receivers]
      (let [p (promise)]
        (actor/send r :get p)
        (is (= 1 @p))))))

(deftest tx-to-tx
  "Send from transaction. Receiver contains transaction."
  (let [n-send (stm/ref 0) ; contentious
        n-receive (stm/ref 0) ; contentious
        sender (actor/behavior [rcv]
                 [:do]
                   (stm/dosync
                     (actor/send rcv :inc)
                     (stm/alter n-send inc)))
        receiver (actor/behavior []
                   [:inc]
                     (stm/dosync
                       (stm/alter n-receive inc)))
        receivers (doall (repeatedly 100 #(actor/spawn receiver [])))
        senders   (doall (map #(actor/spawn sender [%]) receivers))]
    (doseq [s senders]
      (actor/send s :do))
    (Thread/sleep 500)
    (is (= 100 (stm/deref n-send)))
    (is (= 100 (stm/deref n-receive)))))

(deftest tx-to-two-tx
  "Send from transaction. Receiver contains multiple transactions."
  (let [n-send (stm/ref 0) ; contentious
        n-receive (stm/ref 0) ; contentious
        sender (actor/behavior [rcv]
                 [:do]
                   (stm/dosync
                     (actor/send rcv :inc)
                     (stm/alter n-send inc)))
        receiver (actor/behavior []
                   [:inc]
                     (do
                       (stm/dosync
                         (stm/alter n-receive inc))
                       (stm/dosync
                         (stm/alter n-receive inc))))
        receivers (doall (repeatedly 100 #(actor/spawn receiver [])))
        senders   (doall (map #(actor/spawn sender [%]) receivers))]
    (doseq [s senders]
      (actor/send s :do))
    (Thread/sleep 500)
    (is (= 100 (stm/deref n-send)))
    (is (= 200 (stm/deref n-receive)))))

(deftest two-tx-to-non-tx
  "Send from and outside transaction. Receiver contains no transaction."
  (let [n-send (stm/ref 0) ; contentious
        sender (actor/behavior [rcv]
                 [:do]
                   (do
                     (actor/send rcv :inc)
                     (stm/dosync
                       (actor/send rcv :inc)
                       (stm/alter n-send inc))
                     (actor/send rcv :inc)
                     (stm/dosync
                       (actor/send rcv :inc)
                       (stm/alter n-send inc))
                     (actor/send rcv :inc)))
        receiver (actor/behavior [i]
                   [:inc]
                     (become :self [(inc i)])
                   [:get promise]
                     (deliver promise i))
        receivers (doall (repeatedly 100 #(actor/spawn receiver [0])))
        senders   (doall (map #(actor/spawn sender [%]) receivers))]
    (doseq [s senders]
      (actor/send s :do))
    (Thread/sleep 500)
    (is (= 200 (stm/deref n-send)))
    (doseq [r receivers]
      (let [p (promise)]
        (actor/send r :get p)
        (is (= 5 @p))))))

(deftest tx-to-tx-to-tx
  "Tx -> tx -> tx"
  (let [n-first (stm/ref 0) ; contentious
        n-second (stm/ref 0) ; contentious
        n-third (stm/ref 0) ; contentious
        first  (actor/behavior [i second]
                 [:do]
                   (stm/dosync
                     (actor/send second :inc)
                     (stm/alter n-first inc)
                     (become :self [(inc i) second])))
        second (actor/behavior [i third]
                 [:inc]
                   (stm/dosync
                     (actor/send third :inc)
                     (stm/alter n-second inc)
                     (become :self [(inc i) third])))
        third  (actor/behavior [i]
                 [:inc]
                   (stm/dosync
                     (stm/alter n-third inc)
                     (become :self [(inc i)]))
                 [:get promise]
                   (deliver promise i))
        thirds  (doall (repeatedly 100 #(actor/spawn third [0])))
        seconds (doall (map #(actor/spawn second [0 %]) thirds))
        firsts  (doall (map #(actor/spawn first [0 %]) seconds))]
    (doseq [f firsts]
      (actor/send f :do))
    (Thread/sleep 500)
    (is (= 100 (stm/deref n-first)))
    (is (= 100 (stm/deref n-second)))
    (is (= 100 (stm/deref n-third)))
    (doseq [t thirds]
      (let [p (promise)]
        (actor/send t :get p)
        (is (= 1 @p))))))

(deftest tx-to-non-tx-to-tx
  "Tx -> non-tx -> tx"
  (let [n-first (stm/ref 0) ; contentious
        n-second (stm/ref 0) ; contentious
        n-third (stm/ref 0) ; contentious
        first  (actor/behavior [i second]
                 [:do]
                   (stm/dosync
                     (actor/send second :inc)
                     (stm/alter n-first inc)
                     (become :self [(inc i) second])))
        second (actor/behavior [i third]
                 [:inc]
                   (do
                     (actor/send third :inc)
                     (become :self [(inc i) third]))
                 [:get promise]
                   (deliver promise i))
        third  (actor/behavior [i]
                 [:inc]
                   (stm/dosync
                     (stm/alter n-third inc)
                     (become :self [(inc i)]))
                 [:get promise]
                   (deliver promise i))
        thirds  (doall (repeatedly 100 #(actor/spawn third [0])))
        seconds (doall (map #(actor/spawn second [0 %]) thirds))
        firsts  (doall (map #(actor/spawn first [0 %]) seconds))]
    (doseq [f firsts]
      (actor/send f :do))
    (Thread/sleep 500)
    (is (= 100 (stm/deref n-first)))
    (doseq [s seconds]
      (let [p (promise)]
        (actor/send s :get p)
        (is (= 1 @p))))
    (is (= 100 (stm/deref n-third)))
    (doseq [t thirds]
      (let [p (promise)]
        (actor/send t :get p)
        (is (= 1 @p))))))
