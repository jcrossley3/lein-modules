(ns lein-modules.plugin
  (:use [leiningen.modules :only (config parent)])
  (:require [leiningen.core.project :as prj]))

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

(defn compositor
  "Returns a reducing function that turns a profile into a composite,
  e.g. {:test {:a 1}} becomes {:test [:test-foo] :test-foo {:a 1}} for
  a project named 'foo'"
  [project]
  (fn [m [k v]]
    (let [n (keyword (format "%s-%s" (name k) (:name project)))]
      (assoc (update-in m [k] #(vec (cons n %))) n v))))

(defn compositize-profiles
  "Return a profile map containing all the profiles found in the
  project and its ancestors, resulting in standard profiles,
  e.g. :test and :dev, becoming composite"
  [project]
  (loop [p project, result nil]
    (if (nil? p)
      result
      (recur (parent p) (reduce (compositor p) result (:profiles p))))))

(defn inherited-profiles
  "Return a list of :inherited profiles from the modules config of the
  project and its ancestors"
  [project]
  (->> (config project)
    (map :inherited)
    (remove nil?)
    (map #(dissoc % :profiles))))

(defn reset-without-profiles
  [project]
  (vary-meta project assoc :without-profiles project))

(defn inherit
  "Apply :inherited profiles from parents, where a parent profile
  overrides a grandparent, guarding recursive middleware calls with a
  metadata flag.
  See https://github.com/technomancy/leiningen/issues/1151"
  [project]
  (if (-> project meta :modules-inherited)
    project
    (let [compost (compositize-profiles project)]
      (-> (prj/add-profiles project compost)
        (vary-meta assoc :modules-inherited true)
        (vary-meta update-in [:profiles] merge compost)
        (prj/unmerge-profiles [:default])
        (prj/merge-profiles (inherited-profiles project))
        (reset-without-profiles)
        (prj/merge-profiles [:default])))))

(defn middleware
  "Implicit Leiningen middleware"
  [project]
  (if (-> project config empty?)
    project
    (-> project inherit versionize)))
