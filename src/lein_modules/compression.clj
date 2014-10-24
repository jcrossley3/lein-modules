(ns lein-modules.compression
  "Only necessary because 2.3.4+ expands active profiles, and while
  the child may include the composites, its expansions will be
  different. This isn't foolproof."
  (:require [leiningen.core.project :as prj]
            [clojure.set :refer (subset?)]))

(def ^:private expansions
  "Returns a sequence of pairs where the first is the expansion of the
  second, which is a key to a composite profile, ordered by largest
  expansion"
  (memoize
    (fn [profiles]
      (->> profiles
        (filter (comp prj/composite-profile? second))
        (map (fn [[k v]]
               (loop [[h & t] v, r []]
                 (cond
                   (nil? h) [r k]
                   (prj/composite-profile? (h profiles)) (recur (concat (h profiles) t), r)
                   :else (recur t, (conj r h))))))
        (sort-by (comp - count first))))))

(defn- substitute
  "If found, replace a sub-sequence of a collection with v"
  [coll sub v]
  (if (subset? sub (set coll))
    (conj (remove (set sub) coll) v)
    coll))

(defn compress
  "Compresses expanded profiles into their associated composites"
  [c profiles]
  (let [expands (expansions profiles)]
    (loop [[[sub with] & r] expands, result c]
      (if (empty? sub)
        result
        (recur r (substitute result sub with))))))

(defn compressed-profiles
  [project]
  (compress
    (-> project meta :included-profiles distinct)
    (-> project meta :profiles (dissoc :default :leiningen/default))))
