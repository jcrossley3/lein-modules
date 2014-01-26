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

(def modules-config
  "Traverse all parents to accumulate a list of plugin config,
  ordered by least to most immediate ancestors"
  (memoize
    (fn [project]
      (loop [p project, acc '()]
        (if (nil? p)
          (remove nil? acc)
          (recur (parent p) (conj acc (-> p :modules))))))))

(defn versions
  "Merge dependency management maps of :versions from the
  modules-config"
  [project]
  (->> (modules-config project) (map :versions) (apply merge {})))

(defn replace-keyword
  [d vmap]
  (let [m (prj/dependency-map d)
        v (:version m)]
    (if (keyword? v)
      (prj/dependency-vec (assoc m :version (v vmap)))
      d)))

(defn versionize
  "Substitute keywords with actual versions from the :versions
  modules-config"
  [project]
  (let [vmap (versions project)
        f #(for [d %] (replace-keyword d vmap))]
    (-> project
      (update-in [:dependencies] f)
      (update-in [:plugins] f)          ; chicken or egg?
      (update-in [:parent] replace-keyword vmap))))

(defn inherited-profiles
  "Extract a list of :inherited profiles from the modules-config"
  [project]
  (remove nil? (->> (modules-config project) (map :inherited))))

(defn inherit
  "Apply :inherited profiles from parents, where a parent profile
  overrides a grandparent"
  [project]
  (unless-merging project
    (prj/merge-profiles project (inherited-profiles project))))

(defn middleware
  "Implicit Leiningen middleware"
  [project]
  (-> project inherit versionize))
