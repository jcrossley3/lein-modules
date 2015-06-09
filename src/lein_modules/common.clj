(ns lein-modules.common
  (:require [leiningen.core.project :as prj]
            [leiningen.core.main :refer (version-satisfies? leiningen-version)]
            [leiningen.core.utils :as utils]
            [clojure.java.io :as io]
            [lein-modules.compression :refer (compressed-profiles)]))

(def read-project (if (version-satisfies? (leiningen-version) "2.5")
                    (load-string "#(prj/init-profiles (prj/project-with-profiles (prj/read-raw %)) [:default])")
                    prj/read))

(def ^:dynamic *middleware-disabled* false)

(defmacro without-middleware [& body]
  `(binding [*middleware-disabled* true]
     ~@body))

(defmacro with-middleware [project & body]
  `(if (or *middleware-disabled* (-> ~project meta ::middleware-applied))
     ~project
     (let [~project (vary-meta ~project assoc ::middleware-applied true)]
       (vary-meta (do ~@body) dissoc ::middleware-applied))))

(defn with-profiles
  "Apply profiles to project"
  [project profiles]
  (when project
    (let [profiles (filter (set profiles) (-> project meta :profiles keys))]
      (prj/set-profiles project profiles))))

(defn parent
  "Return the project's parent project"
  ([project]
     (parent project (compressed-profiles project)))
  ([project profiles]
     (let [p (get-in project [:modules :parent] ::none)]
       (cond
         (map? p) p                        ; handy for testing
         (not p) nil                       ; don't search for parent
         :else (as-> (if (= p ::none) nil p) $
                 (or $ (-> project :parent prj/dependency-map :relative-path) "..")
                 (.getCanonicalFile (io/file (:root project) $))
                 (if (.isDirectory $) $ (.getParentFile $))
                 (io/file $ "project.clj")
                 (when (.exists $) (read-project (str $)))
                 (when $ (with-profiles $ profiles)))))))

(defn config
  "Traverse all parents to accumulate a list of :modules config,
  ordered by least to most immediate ancestors"
  [project]
  (without-middleware
    (loop [p project, acc '()]
      (if (nil? p)
        (remove nil? acc)
        (recur (parent p) (conj acc (-> p :modules)))))))

(defn id
  "Returns fully-qualified symbol identifier for project"
  [project]
  (if project
    (symbol (:group project) (:name project))))

(defn child?
  "Return true if child is an immediate descendant of project"
  [project child]
  (= (:root project) (:root (parent child))))

(defn file-seq-sans-symlinks
  "A tree seq on java.io.Files that aren't symlinks"
  [dir]
  (tree-seq
    (fn [^java.io.File f] (and (.isDirectory f) (not (utils/symlink? f))))
    (fn [^java.io.File d] (seq (.listFiles d)))
    dir))

(defn children
  "Return the child maps for a project according to its active profiles"
  [project]
  (if-let [dirs (-> project :modules :dirs)]
    (remove nil?
      (map (comp #(try (read-project %) (catch Exception e (println (.getMessage e))))
             (memfn getCanonicalPath)
             #(io/file (:root project) % "project.clj"))
        dirs))
    (->> (file-seq-sans-symlinks (io/file (:root project)))
      (filter #(= "project.clj" (.getName %)))
      (remove #(= (:root project) (.getParent %)))
      (map (comp #(try (read-project %) (catch Exception e (println (.getMessage e)))) str))
      (remove nil?)
      (filter #(child? project (with-profiles % (compressed-profiles project)))))))

(defn progeny
  "Recursively return the project's children in a map keyed by id"
  ([project]
     (progeny project (compressed-profiles project)))
  ([project profiles]
     (let [kids (children (with-profiles project profiles))]
       (apply merge
         (into {} (map (juxt id identity) kids))
         (->> kids
           (remove #(= (:root project) (:root %))) ; in case "." in :dirs
           (map #(progeny % profiles)))))))

(defn primogenitor
  "The top-most parent of a project"
  [project]
  (if-let [p (parent project nil)]
    (recur p)
    project))
