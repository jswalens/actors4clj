# actors4clj

Extremely simple actors library for Clojure.

This library implements "classic" (or "Agha-style") actors for Clojure, using macro's. It is extremely simple (fewer than 100 lines of code), with the goal of being easy to extend and experiment with.

## Usage

There are five constructs:

* `(behavior [x ...] [pattern] (response) ...)` defines a behavior, which specifies how an actor should respond to incoming messages.
* `(spawn beh [v ...])` spawns a new actor with the given behavior and internal state.
* `(send actor1 v ...)` asynchronously sends a message to an actor.
* `(become beh [v ...])` updates the current actor's behavior and internal state.
* `self` is a global variable that always refers to the current actor.

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
        (become :self [(+ i j)]))))

(def counter1 (spawn counter [0]))
(send counter1 :inc)
(send counter1 :add 5)
(send counter1 :get)   ; => 6
(send counter1 :add 4)
(send counter1 :get)   ; => 10
```

A behavior contains:
* a list of parameters, which form its "internal state".
* a list of patterns and corresponding code blocks. Incoming messages are compared to the patterns (using core.match), and the code corresponding with the first match is executed.

## License

Licensed under the MIT license, included in the file `LICENSE`.
