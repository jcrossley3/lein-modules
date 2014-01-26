(ns leiningen.modules
  (:require [leiningen.core.project :as prj]
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

(defn child?
  "Return true if child is an immediate descendant of project"
  [project child]
  (= (:root project) (:root (parent child))))

(defn children
  "Return the child modules for a project"
  [project]
  (->> (file-seq (io/file (:root project))) 
    (filter #(= "project.clj" (.getName %)))
    (map (comp prj/read str))
    (filter (partial child? project))))

(def config
  "Traverse all parents to accumulate a list of plugin config,
  ordered by least to most immediate ancestors"
  (memoize
    (fn [project]
      (loop [p project, acc '()]
        (if (nil? p)
          (remove nil? acc)
          (recur (parent p) (conj acc (-> p :modules))))))))

(defn modules
  "I don't do a lot."
  [project & args]
  (println "Hi!"))
