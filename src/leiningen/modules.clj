(ns leiningen.modules
  (:use [clojure.set :only (intersection union)])
  (:require [leiningen.core.project :as prj]
            [leiningen.core.main :as main]
            [clojure.java.io :as io]))

(defn parent
  "Return the parent map of the passed project"
  [project]
  (when-let [path (-> project :parent prj/dependency-map :relative-path)]
    (prj/read (-> (.. (io/file (:root project) path)
                    getCanonicalFile
                    getParentFile)
                (io/file "project.clj")
                str))))

(def config
  "Traverse all parents to accumulate a list of plugin config,
  ordered by least to most immediate ancestors"
  (memoize
    (fn [project]
      (loop [p project, acc '()]
        (if (nil? p)
          (remove nil? acc)
          (recur (parent p) (conj acc (-> p :modules))))))))

(defn child?
  "Return true if child is an immediate descendant of project"
  [project child]
  (= (:root project) (:root (parent child))))

(defn children
  "Return the child modules for a project"
  [project]
  (if-let [dirs (-> project :modules :dirs)]
    (map (comp prj/read
           (memfn getCanonicalPath)
           #(io/file (:root project) % "project.clj"))
      dirs)
    (->> (file-seq (io/file (:root project))) 
      (filter #(= "project.clj" (.getName %)))
      (map (comp prj/read str))
      (filter (partial child? project)))))

(defn id
  "Returns fully-qualified symbol identifier for project"
  [project]
  (symbol (:group project) (:name project)))

(defn deep-deps
  "Returns a set of symbols denoting all the dependencies of a
  project and its descendants"
  [project]
  (let [deps (set (->> project :dependencies (map first)))
        kids (children project)]
    (if (empty? kids)
      deps
      (apply union deps (map deep-deps kids)))))

(defn topological-sort [deps]
  "A topological sort of a mapping of graph nodes to their edges (credit Jon Harrop)"
  (loop [deps deps resolved #{} result []]
    (if (empty? deps)
      result
      (if-let [dep (some (fn [[k v]] (if (empty? (remove resolved v)) k)) deps)]
        (recur (dissoc deps dep) (conj resolved dep) (conj result dep))
        (throw (Exception. "Cyclic dependency!"))))))

(defn ordered-builds
  "Represent the inter-dependence of child projects and apply a topological sort"
  [project]
  (let [m (reduce #(assoc % (id %2) %2) {} (children project))
        builds (set (keys m))
        deps (reduce (fn [acc p]
                       (assoc acc (id p)
                              (intersection builds (deep-deps p))))
               {} (vals m))]
    (map m (topological-sort deps))))

(defn modules
  "Run a task in all child projects in dependent order"
  [project task & args]
  (let [modules (ordered-builds project)]
    (println "------------------------------------------------------------------------")
    (println " Module build order:")
    (doseq [p modules]
      (println "  " (id p)))
    (doseq [project modules]
      (let [project (prj/init-project project)
            task (main/lookup-alias task project)]
        (println "------------------------------------------------------------------------")
        (println " Building" (:name project) (:version project))
        (println "------------------------------------------------------------------------")
        (main/apply-task task project args)))))
