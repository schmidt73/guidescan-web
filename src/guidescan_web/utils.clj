(ns guidescan-web.utils)

(defmacro if-let*
  "Multiple binding version of if-let"
  ([bindings then]
   `(if-let* ~bindings ~then nil))
  ([bindings then else]
   (assert (vector? bindings) "a vector for its binding")
   (assert (even? (count bindings)) "exactly even forms in binding vector")
   (let [if-let-else (keyword (name (gensym "if_let_else__")))
         inner (fn inner [bindings]
                 (if (seq bindings)
                   `(if-let [~(first bindings) ~(second bindings)]
                      ~(inner (drop 2 bindings))
                      ~if-let-else)
                   then))]
     `(let [temp# ~(inner bindings)]
        (if (= temp# ~if-let-else) ~else temp#)))))

(defmacro if-some*
  "Multiple binding version of if-some"
  ([bindings then]
   `(if-some* ~bindings ~then nil))
  ([bindings then else]
   (assert (vector? bindings) "a vector for its binding")
   (assert (even? (count bindings)) "exactly even forms in binding vector")
   (let [if-some-else (keyword (name (gensym "if_some_else__")))
         inner (fn inner [bindings]
                 (if (seq bindings)
                   `(if-some [~(first bindings) ~(second bindings)]
                      ~(inner (drop 2 bindings))
                      ~if-some-else)
                   then))]
     `(let [temp# ~(inner bindings)]
        (if (= temp# ~if-some-else) ~else temp#)))))

(defn distinct-by
  "Returns a lazy sequence of the elements of coll removing duplicates of (f item).
  Returns a stateful transducer when no collection is provided."
  {:added "1.0"
   :static true}
  ([f]
   (fn [rf]
     (let [seen (volatile! #{})]
       (fn
         ([] (rf))
         ([result] (rf result))
         ([result input]
          (if (contains? @seen input)
            result
            (do (vswap! seen conj input)
                (rf result input))))))))
  ([f coll]
   (let [step (fn step [xs seen]
                (lazy-seq
                 ((fn [[h :as xs] seen]
                    (when-let [s (seq xs)]
                      (if (contains? seen (f h))
                        (recur (rest s) seen)
                        (cons h (step (rest s) (conj seen (f h)))))))
                  xs seen)))]
     (step coll #{}))))

(defn revcom
  [sequence]
  (-> (map {\A \T \T \A \G \C \C \G \N \N} sequence)
      (reverse)
      (clojure.string/join)))
