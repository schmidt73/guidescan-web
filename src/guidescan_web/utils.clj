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

(defn revcom
  [sequence]
  (-> (map {\A \T \T \A \G \C \C \G \N \N} sequence)
      (reverse)
      (clojure.string/join)))
