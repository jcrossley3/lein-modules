(ns lein-modules.common
  (:require [leiningen.core.project :as prj]
            [clojure.java.io :as io]))

(defn parent
  "Return the project's parent project"
  [project]
  (let [p (-> project :modules :parent)]
    (if (map? p) ; handy for testing
      p
      (if-let [path (or p (-> project :parent prj/dependency-map :relative-path))]
        (prj/read (-> (.. (io/file (:root project) path)
                        getCanonicalFile
                        getParentFile)
                    (io/file "project.clj")
                    str))))))

(defn config
  "Traverse all parents to accumulate a list of :modules config,
  ordered by least to most immediate ancestors"
  [project]
  (loop [p project, acc '()]
    (if (nil? p)
      (remove nil? acc)
      (recur (parent p) (conj acc (-> p :modules))))))
