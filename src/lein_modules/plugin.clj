(ns lein-modules.plugin
  (:require [leiningen.core.project :as prj]
            [clojure.java.io :as io]))

;;; A hack to prevent recursive middleware calls
;;; https://github.com/technomancy/leiningen/issues/1151
(def ^:dynamic *merging* nil)
(defmacro unless-merging [default & body]
  `(if *merging*
     ~default
     (binding [*merging* true]
       ~@body)))

(defn parent
  "Return the parent map of the passed project"
  [project]
  (when-let [path (-> project :parent prj/dependency-map :relative-path)]
    (prj/read (-> (.. (io/file (:root project) path)
                    getCanonicalFile
                    getParentFile)
                (io/file "project.clj")
                str))))

(defn inherited-profiles
  "Traverse all parents to accumulate a list of :inherited profiles,
  ordered by least to most immediate ancestors"
  [project]
  (loop [p project, acc '()]
    (if (nil? p)
      (remove nil? acc)
      (recur (parent p) (conj acc (-> p :profiles :inherited))))))

(defn inherit
  "Apply :inherited profiles from parents, where a parent profile
  overrides a grandparent"
  [project]
  (unless-merging project
    (prj/merge-profiles project (inherited-profiles project))))

(defn middleware
  "Implicit Leiningen middleware"
  [project]
  (inherit project))
