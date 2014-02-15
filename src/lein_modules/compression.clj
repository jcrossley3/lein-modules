(ns lein-modules.compression
  (:require [leiningen.core.project :as prj]))

(defn expansions
  [profiles]
  (->> profiles
    (filter (fn [[_ v]] (prj/composite-profile? v)))
    (map (fn [[k v]]
           (loop [[h & t] v, r []]
             (cond
               (nil? h) [r k]
               (prj/composite-profile? (h profiles)) (recur (concat (h profiles) t), r)
               :else (recur t, (conj r h))))))
    (sort-by (comp count first))
    reverse))

(defn replace-subvec
  [vect sub with]
  (vec (loop [v vect, result []]
         (cond
           (empty? v) result
           (< (count v) (count sub)) (concat result v)
           (= sub (subvec v 0 (count sub))) (recur (subvec v (count sub)) (conj result with))
           :else (recur (subvec v 1) (conj result (first v)))))))

(defn compress
  "Compresses expanded profiles into their associated composites"
  [vect profiles]
  (let [expands (expansions profiles)]
    (loop [[[sub with] & r] expands, result (vec vect)]
      (if (empty? sub)
        result
        (recur r (replace-subvec result sub with))))))

