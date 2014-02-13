(ns leiningen.modules
  (:require [leiningen.core.project :as prj]
            [leiningen.core.main :as main]
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

(defn child?
  "Return true if child is an immediate descendant of project"
  [project child]
  (= (:root project) (:root (parent child))))

(defn unexpand-profiles
  "Compress a list of active profile symbols into their composite representaions"
  [actives profiles]
  (if (= [:default] actives)
    actives
    (->> profiles
      (filter (fn [[k v]] (prj/composite-profile? v)))
      (map first)
      (filter #(let [p (profiles %)] (= p (filter (set p) actives)))))))

(defn read-child
  "Read a project with the same profiles active as the parent"
  [parent file]
  (let [actives (-> parent meta :active-profiles distinct)
        profiles (-> parent meta :profiles (dissoc :default))
        unexpanded (unexpand-profiles actives profiles)]
    (println "DEBUG: prj/read" file unexpanded)
    (prj/read file unexpanded)))

(defn children
  "Return the child modules for a project"
  [project]
  (let [reader (partial read-child project)]
    (if-let [dirs (-> project :modules :dirs)]
      (map (comp reader
             (memfn getCanonicalPath)
             #(io/file (:root project) % "project.clj"))
        dirs)
      (->> (file-seq (io/file (:root project))) 
        (filter #(= "project.clj" (.getName %)))
        (remove #(= (:root project) (.getParent %)))
        (map (comp reader str))
        (filter (partial child? project))))))

(defn progeny
  "Recursively return the project's children"
  [project]
  (let [children (children project)]
    (if (empty? children)
      [project]
      (cons project (mapcat progeny children)))))

(defn id
  "Returns fully-qualified symbol identifier for project"
  [project]
  (if project
    (symbol (:group project) (:name project))))

(defn topological-sort [deps]
  "A topological sort of a mapping of graph nodes to their edges (credit Jon Harrop)"
  (loop [deps deps, resolved #{}, result []]
    (if (empty? deps)
      result
      (if-let [dep (some (fn [[k v]] (if (empty? (remove resolved v)) k)) deps)]
        (recur (dissoc deps dep) (conj resolved dep) (conj result dep))
        (throw (Exception. (apply str "Cyclic dependency: " (interpose ", " (keys deps)))))))))

(defn interdependents
  "Return a project's dependency symbols in common with targets"
  [project targets]
  (->> (cons [(id (parent project))] (:dependencies project))
    (map first)
    (filter (set targets))))

(defn ordered-builds
  "Represent the inter-dependence of project descendants and apply a
  topological sort"
  [project]
  (let [all  (reduce #(assoc % (id %2) %2) {} (progeny project))
        tgts (keys all)
        deps (reduce
               (fn [acc p] (assoc acc (id p) (interdependents p tgts)))
               {}
               (vals all))]
    (map all (topological-sort deps))))

(defn modules
  "Run a task in all related projects in dependent order"
  [project task & args]
  (let [modules (ordered-builds project)]
    (println "------------------------------------------------------------------------")
    (println " Module build order:")
    (doseq [p modules]
      (println "  " (:name p)))
    (doseq [project modules]
      (println "------------------------------------------------------------------------")
      (println " Building" (:name project) (:version project))
      (println "------------------------------------------------------------------------")
      (let [project (prj/init-project project)
            task (main/lookup-alias task project)]
        (main/apply-task task project args)))))
