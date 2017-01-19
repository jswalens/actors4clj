# actors4clj

Extremely simple actors library for Clojure.

## Usage

Example:

```
(def counter
  (behavior [i]
    [:get]
      (println "count:" i)
    [:inc]
      (do
        (println "count:" i "+ 1")
        (become :self [(+ i 1)]))
    [:add j]
      (do
        (println "count:" i "+" j)
        (become :self [(+ i j)])))

(def counter1 (spawn counter [0]))
(send counter1 :inc)
(send counter1 :add 5)
(send counter1 :get)   ; => 6
(send counter1 :add 4)
(send counter1 :get)   ; => 10
```

## License

Distributed under the MIT License.
