(ns lein-modules.versionization
  (:use [lein-modules.common :only (config)]))

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
