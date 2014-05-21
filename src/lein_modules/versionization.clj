(ns lein-modules.versionization
  (:use [lein-modules.common :only (config)]
        [leiningen.core.project :only (artifact-map composite-profile?)]))

(defn versions
  "Merge dependency management maps of :versions from the
  modules config"
  [project]
  (->> (config project) (map :versions) (apply merge {})))

(defn recursive-get
  "There's probably a better way to do this"
  [k m]
  (let [v (get m k)]
    (if (contains? m v)
      (recur v m)
      v)))

(defn expand-version
  "Recursively search for a version string in the vmap using the first
  non-nil result of trying the following keys, in order: the
  fully-qualified id field of the dependency vector, its version
  field, just the artifact id, and then finally just the group id. If
  none of those are found, just return the version."
  [d vmap]
  (when-let [[id ver & opts] d]
    (apply vector id
      (or
        (recursive-get id vmap)
        (recursive-get ver vmap)
        (recursive-get (-> id artifact-map :artifact-id symbol) vmap)
        (recursive-get (-> id artifact-map :group-id symbol) vmap)
        ver)
      opts)))

(defn versionize
  "Substitute versions in dependency vectors with actual versions from
  the :versions modules config"
  [project]
  (let [vmap (versions project)
        f #(with-meta (for [d %] (expand-version d vmap)) (meta %))
        fd #(if-let [x (:dependencies %)] (assoc % :dependencies (f x)) %)
        ff #(into {} (for [[k v] %]
                       (if (composite-profile? v)
                         [k (vec (map fd v))]
                         [k (fd v)])))]
    (-> project
      (update-in [:dependencies] f)
      (update-in [:parent] expand-version vmap)
      (update-in [:profiles] ff)
      (vary-meta update-in [:without-profiles :dependencies] f)
      (vary-meta update-in [:profiles] ff))))
