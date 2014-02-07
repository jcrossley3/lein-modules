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

(defn expand-version
  "Recursively search for a version string in the vmap using the first
  field of the dependency vector. If not found, use the dependency's
  version as a key, and if that's not found, just return the version."
  [d vmap]
  (if-let [[id ver & opts] d]
    (loop [k (id vmap)]
      (if (contains? vmap k)
        (recur (k vmap))
        (apply vector id (or k (vmap ver) ver) opts)))))

(defn versionize
  "Substitute versions in dependency vectors with actual versions from
  the :versions modules config"
  [project]
  (let [vmap (versions project)
        f #(for [d %] (expand-version d vmap))]
    (-> project
      (update-in [:dependencies] f)
      (update-in [:parent] expand-version vmap))))

(defn active-profiles
  "Return the active profile keywords in effect for a given project
   (TODO: use prj/expand-profile)"
  [project]
  (distinct
    (loop [[p & r] (-> project meta :active-profiles), result []]
      (if (nil? p)
        result
        (let [profile (p (:profiles project) (@prj/default-profiles p))]
          (if (prj/composite-profile? profile)
            (recur (concat profile r) result)
            (recur r (conj result p))))))))

(defn allowed-profiles
  "Given a list of actives and list of allowed, return the intersection in 'actives order'"
  [actives allowed]
  (if (nil? allowed)
    actives
    (filter (set allowed) actives)))

(defn expand-profiles
  "Given a list of active profile keywords and a :modules config map,
  return the :inherited profile along with any active profiles from
  the associated project"
  [actives config]
  (let [inherited (:inherited config)]
    (cons inherited
      (map #(% (-> config meta :project :profiles))
        (allowed-profiles actives (:profiles inherited))))))

(defn inherited-profiles
  "Extract a list of :inherited profiles from the modules config, as
  well as any active profiles from parents containing a :modules
  entry"
  [project]
  (->> (config project)
    (mapcat (partial expand-profiles (active-profiles project)))
    (remove nil?)
    (map #(dissoc % :profiles))))

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
