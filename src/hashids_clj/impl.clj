(ns hashids-clj.impl
  (:use flatland.ordered.set)
  (:require [clojure.math.numeric-tower :as math]))

(def DEFAULT_ALPHABET "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890")
(def DEFAULT_SEPS "cfhistuCFHISTU")
(def DEFAULT_SALT "")
(def DEFAULT_MIN_LENGTH 0)

(def MIN_ALPHABET_LENGTH 16)

(def SEP_DIV (/ 7 2))
(def GUARD_DIV 12)

(defn swap [v i1 i2]
  (assoc v i2 (v i1) i1 (v i2)))

(defn str->charint
  "Return the integer which represents the first character in the string"
  [instr]
  (-> instr
      char-array
      first
      int))

(defn positions
  [pred coll]
  (keep-indexed (fn [idx x]
                  (when (pred x)
                    idx))
                coll))

(defn consistent-shuffle
  [alphabet salt]
  (if (empty? salt)
    alphabet
    (apply str (reduce (fn [[alph p] [idx a]]
                         (let [i (- (count alph) idx 1)
                               v (mod idx (count salt))
                               n (int (nth salt v))
                               p (+ p n)]
                           (if (zero? i)
                             alph
                             [(swap alph i (mod (+ n v p) i)) p])))
                       [(vec alphabet) 0]
                       (map-indexed (fn [idx a] [idx a]) alphabet)))))


(defn enhash
  ;; Known as 'hash' in other implementations
  ([input alphabet] (enhash input alphabet (num input) ""))
  ([input alphabet n res]
   (if (zero? input)
     (subs alphabet 0 1)
     (if (zero? n)
       res
       (recur input
              alphabet
              (int (/ n (count alphabet)))
              (str (nth (cycle alphabet) n) res))))))

(defn dehash
  ;; Known as 'unhash' in other implementations
  [input alphabet]
  (reduce + (map-indexed
             (fn [idx c]
               (let [pos (first (positions #{c} alphabet))]
                 (* pos (math/expt (count alphabet)
                              (- (count input) idx 1)))))
             (vec input))))

(defn encode-numbers
  [seps alphabet salt hash_int numbers]
  (let [lottery  (str (nth alphabet (mod hash_int (count alphabet))))
        sepsc    (cycle seps)]
    (second
     (reduce (fn [[alph ret] [idx n]]
               (let [buf     (concat lottery salt alph)
                     alph    (consistent-shuffle (vec alph) (take (count alph) buf))
                     encchar (enhash n alph)
                     addsep  (nth sepsc (mod n (+ (int (first encchar)) idx)))]
                 (if (< (+ idx 1) (count numbers))
                   [alph (str ret encchar addsep)]
                   [alph (str ret encchar)])))
             [alphabet ""] ;; reduce by passing along alphabet, which is transformed in each iteration
             (map-indexed vector numbers)))))

(defn add-guards
  [min-length guards hash_int hashstr]
  (let [prepend-guard #(if (< (count %) min-length)
                         (str (nth (cycle guards)
                                   (+ hash_int (int (nth % 0))))
                              %)
                         %)
        append-guard #(if (< (count %) min-length)
                        (str %
                             (nth (cycle guards)
                                  (+ hash_int (int (nth % 2)))))
                        %)]
    (->> hashstr
         prepend-guard
         append-guard)))

(defn ensure-min-length
  [min-length alphabet hashstr]
  (let [half_length (int (/ (count alphabet) 2))
        upsize (fn [[alph ret]]
                 (let [alph (consistent-shuffle alph alph)
                       rplusalph (str (subs alph half_length) ret (subs alph 0 half_length))
                       excess (- (count rplusalph) min-length)]
                   (if (> excess 0)
                     [alph (subs rplusalph (int (/ excess 2)) min-length)]
                     [alph rplusalph])))]
    (second (first (drop-while #(< (count (second %)) min-length)
                               (iterate upsize [alphabet hashstr]))))))

(defn strip-whitespace [s]
  (apply str (remove clojure.string/blank? (map str s))))

(defn str-char-intersection
  [a b]
  (let [osa (into (ordered-set) a)
        osb (into (ordered-set) b)]
    (apply str (clojure.set/intersection osa osb))))

(defn str-char-difference
  [a b]
  (let [osa (into (ordered-set) a)
        osb (into (ordered-set) b)]
    (apply str (clojure.set/difference osa osb))))

(defn balance-seps
  "Balance alphabet and seps, the ratio of sizes of which should SEP_DIV"
  [seps alph]
  (let [seps-length (max 2 (math/ceil (/ (count alph) SEP_DIV)))
        seps-diff (- seps-length (count seps))]
    (if (or (zero? (count seps))
            (> (/ (count alph)
                  (count seps))
               SEP_DIV))
      (if (> seps-length (count seps))
        (vec (map clojure.string/join (split-at 2 (str seps alph))))
        [(subs seps 0 seps-length) alph])
      [seps alph])))

(defn extract-guards
  "Take portions of seps or alphabet to make guards"
  [alph seps]
  (let [guard-length (math/ceil (/ (count alph) GUARD_DIV))]
    (if (< (count alph) 3)
      {:guards (subs seps 0 guard-length)
       :seps (subs seps guard-length)
       :alphabet alph}
      {:guards (subs alph 0 guard-length)
       :seps seps
       :alphabet (subs alph guard-length)})))

(defn setup
  ([] (setup {}))
  ([{:keys [seps alphabet salt min-length]
    :or {seps       DEFAULT_SEPS
         alphabet   DEFAULT_ALPHABET
         salt       DEFAULT_SALT
         min-length DEFAULT_MIN_LENGTH}}]
    {:pre  [(>= (count alphabet) MIN_ALPHABET_LENGTH)]}
  (let [alph-unbal (->> (str-char-difference alphabet seps)
                        distinct
                        strip-whitespace)
        seps-unbal (->> (str-char-intersection alphabet seps)
                        distinct
                        strip-whitespace)
        [seps alph] (balance-seps seps-unbal alph-unbal)]
    (assoc (extract-guards (consistent-shuffle alph salt) seps)
      :min-length min-length
      :salt salt))))

(defn encode
  [opts numbers]
  (let [{:keys [seps alphabet salt min-length guards]} (setup opts)
        hash_int (reduce + (map-indexed (fn [idx num] (mod num (+ idx 100))) numbers))]

  (->> numbers
       (encode-numbers seps alphabet salt hash_int)
       (add-guards min-length guards hash_int)
       (ensure-min-length min-length alphabet))))
