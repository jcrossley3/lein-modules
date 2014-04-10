(ns lein-modules.common
  (:require [leiningen.core.project :as prj]
            [clojure.java.io :as io]))

(def read-project (memoize prj/read))

(defn parent
  "Return the project's parent project"
  [project]
  (let [p (get-in project [:modules :parent] ::none)]
    (cond
      (map? p) p                        ; handy for testing
      (not p) nil                       ; don't search for parent
      :else (as-> (if (= p ::none) nil p) $
              (or $ (-> project :parent prj/dependency-map :relative-path) "..")
              (.getCanonicalFile (io/file (:root project) $))
              (if (.isDirectory $) $ (.getParentFile $))
              (io/file $ "project.clj")
              (if (.exists $) (read-project (str $)))))))

(defn config
  "Traverse all parents to accumulate a list of :modules config,
  ordered by least to most immediate ancestors"
  [project]
  (loop [p project, acc '()]
    (if (nil? p)
      (remove nil? acc)
      (recur (parent p) (conj acc (-> p :modules))))))
