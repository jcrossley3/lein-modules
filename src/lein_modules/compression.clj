(ns lein-modules.compression
  "Only necessary because 2.3.4+ expands active profiles, and while
  the child may include the composites, its expansions will be
  different. This isn't foolproof."
  (:require [leiningen.core.project :as prj]))

(def ^:private expansions
  "Returns a sequence of pairs where the first is the expansion of the
  second, which is a key in profiles, ordered by largest expansion"
  (memoize
    (fn [profiles]
      (->> profiles
        (filter (fn [[_ v]] (prj/composite-profile? v)))
        (map (fn [[k v]]
               (loop [[h & t] v, r []]
                 (cond
                   (nil? h) [r k]
                   (prj/composite-profile? (h profiles)) (recur (concat (h profiles) t), r)
                   :else (recur t, (conj r h))))))
        (sort-by (comp - count first))))))

(defn- replace-subvec
  "Replace a sub-sequence within a vector with something else"
  [vect sub with]
  (vec (loop [v vect, result []]
         (cond
           (< (count v) (count sub))
           (concat result v)

           (= sub (subvec v 0 (count sub)))
           (recur (subvec v (count sub)) (conj result with))

           :else (recur (subvec v 1) (conj result (first v)))))))

(defn compress
  "Compresses expanded profiles into their associated composites"
  [vect profiles]
  (let [expands (expansions profiles)]
    (loop [[[sub with] & r] expands, result (vec vect)]
      (if (empty? sub)
        result
        (recur r (replace-subvec result sub with))))))

