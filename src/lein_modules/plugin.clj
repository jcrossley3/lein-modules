(ns lein-modules.plugin
  (:use [leiningen.modules :only (config)])
  (:require [leiningen.core.project :as prj]))

;;; A hack to prevent recursive middleware calls
;;; https://github.com/technomancy/leiningen/issues/1151
(def ^:dynamic *merging* nil)
(defmacro unless-merging [default & body]
  `(if *merging*
     ~default
     (binding [*merging* true]
       ~@body)))

(defn versions
  "Merge dependency management maps of :versions from the
  modules config"
  [project]
  (->> (config project) (map :versions) (apply merge {})))

(defn replace-keyword
  [d vmap]
  (let [m (prj/dependency-map d)
        v (:version m)]
    (if (keyword? v)
      (prj/dependency-vec (assoc m :version (v vmap)))
      d)))

(defn versionize
  "Substitute keywords with actual versions from the :versions
  modules config"
  [project]
  (let [vmap (versions project)
        f #(for [d %] (replace-keyword d vmap))]
    (-> project
      (update-in [:dependencies] f)
      (update-in [:plugins] f)          ; chicken or egg?
      (update-in [:parent] replace-keyword vmap))))

(defn inherited-profiles
  "Extract a list of :inherited profiles from the modules config"
  [project]
  (remove nil? (->> (config project) (map :inherited))))

(defn inherit
  "Apply :inherited profiles from parents, where a parent profile
  overrides a grandparent"
  [project]
  (unless-merging project
    (prj/merge-profiles project (inherited-profiles project))))

(defn middleware
  "Implicit Leiningen middleware"
  [project]
  (if (-> project config empty?)
    project
    (-> project inherit versionize)))
