(ns leiningen.modules
  (:require [leiningen.core.project :as prj]
            [leiningen.core.main :as main]
            [leiningen.core.eval :as eval]
            [leiningen.core.utils :as utils]
            [clojure.java.io :as io]
            [clojure.string :as s])
  (:use [lein-modules.inheritance :only (inherit)]
        [lein-modules.common      :only (parent)]
        [lein-modules.compression :only (compress)]))

(defn child?
  "Return true if child is an immediate descendant of project"
  [project child]
  (= (:root project) (:root (parent child))))

(defn compress-profiles
  [project]
  (compress
    (-> project meta :included-profiles distinct)
    (-> project meta :profiles)))

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
    (map (comp prj/read (memfn getCanonicalPath)
           #(io/file (:root project) % "project.clj"))
      dirs)
    (->> (file-seq-sans-symlinks (io/file (:root project)))
      (filter #(= "project.clj" (.getName %)))
      (remove #(= (:root project) (.getParent %)))
      (map (comp #(try (prj/read %) (catch Exception _)) str))
      (remove nil?)
      (filter #(child? project (prj/set-profiles (inherit %) (compress-profiles project)))))))

(defn id
  "Returns fully-qualified symbol identifier for project"
  [project]
  (if project
    (symbol (:group project) (:name project))))

(defn progeny
  "Recursively return the project's children in a map keyed by id"
  [project]
  (let [kids (children project)]
    (apply merge
      (into {} (map (juxt id identity) kids))
      (->> kids
        (remove #(= (:root project) (:root %)))
        (map progeny)))))

(defn interdependence
  "Turn a progeny map (symbols to projects) into a mapping of projects
  to their dependent projects"
  [pm]
  (let [deps (fn [p] (->> (:dependencies p)
                      (map first)
                      (map pm)
                      (remove nil?)))]
    (reduce (fn [acc [_ p]] (assoc acc p (deps p))) {} pm)))

(defn topological-sort [deps]
  "A topological sort of a mapping of graph nodes to their edges (credit Jon Harrop)"
  (loop [deps deps, resolved #{}, result []]
    (if (empty? deps)
      result
      (if-let [dep (some (fn [[k v]] (if (empty? (remove resolved v)) k)) deps)]
        (recur (dissoc deps dep) (conj resolved dep) (conj result dep))
        (throw (Exception. (apply str "Cyclic dependency: " (interpose ", " (map :name (keys deps))))))))))

(def ordered-builds
  "Sort a representation of interdependent projects topologically"
  (comp topological-sort interdependence progeny))

(defn create-checkouts
  "Create checkout symlinks for interdependent projects"
  [projects]
  (doseq [[project deps] projects]
    (when-not (empty? deps)
      (let [dir (io/file (:root project) "checkouts")]
        (when-not (.exists dir)
          (.mkdir dir))
        (println "Checkouts for" (:name project))
        (binding [eval/*dir* dir]
          (doseq [dep deps]
            (eval/sh "rm" "-f" (:name dep))
            (eval/sh "ln" "-sv" (:root dep) (:name dep))))))))

(def checkout-dependencies
  "Setup checkouts/ for a project and its interdependent children"
  (comp create-checkouts interdependence progeny))

(defn with-profiles
  "Set the profiles in the args unless some are already there"
  [profiles args]
  (if (some #{"with-profile" "with-profiles"} args)
    args
    (with-meta (concat
                 ["with-profile" (->> profiles
                                   (map name)
                                   (interpose ",")
                                   (apply str))]
                 args)
      {:profiles-added true})))

(defn dump-profiles
  [args]
  (if (-> args meta :profiles-added)
    (str "(" (second args) ")")
    ""))

(defn modules
  "Run a task for all related projects in dependency order.

Any task (along with any arguments) will be run in this project and
then each of this project's child modules. For example:

  $ lein modules install
  $ lein modules deps :tree
  $ lein modules do clean, test
  $ lein modules analias

You can create 'checkout dependencies' for all interdependent modules
by including the :checkouts flag:

  $ lein modules :checkouts

And you can limit which modules run the task with the :dirs option:

  $ lein modules :dirs core,web install

Delimited by either comma or colon, this list of relative paths
will override the [:modules :dirs] config in project.clj"
  [project & args]
  (condp = (first args)
    ":checkouts" (do
                   (checkout-dependencies project)
                   (apply modules project (remove #{":checkouts"} args)))
    ":dirs" (let [dirs (s/split (second args) #"[:,]")]
              (apply modules
                (-> project
                  (assoc-in [:modules :dirs] dirs)
                  (vary-meta assoc-in [:without-profiles :modules :dirs] dirs))
                (drop 2 args)))
    nil nil                             ; do nothing if no task passed
    (let [modules (ordered-builds project)
          profiles (compress-profiles project)
          args (with-profiles profiles args)
          subprocess (get-in project [:modules :subprocess] (or (System/getenv "LEIN_CMD") "lein"))]
      (if (empty? modules)
        (println "No modules found")
        (do
          (println "------------------------------------------------------------------------")
          (println " Module build order:")
          (doseq [p modules]
            (println "  " (:name p)))
          (doseq [project modules]
            (println "------------------------------------------------------------------------")
            (println " Building" (:name project) (:version project) (dump-profiles args))
            (println "------------------------------------------------------------------------")
            (if-let [cmd (get-in project [:modules :subprocess] subprocess)]
              (binding [eval/*dir* (:root project)]
                (let [exit-code (apply eval/sh (cons cmd args))]
                  (when (pos? exit-code)
                    (throw (ex-info "Subprocess failed" {:exit-code exit-code})))))
              (let [project (prj/init-project project)
                    task (main/lookup-alias (first args) project)]
                (main/apply-task task project (rest args))))))))))
