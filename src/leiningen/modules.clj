(ns leiningen.modules
  (:require [leiningen.core.project :as prj]
            [leiningen.core.main :as main]
            [leiningen.core.eval :as eval]
            [leiningen.core.utils :as utils]
            [clojure.string :as s]
            [clojure.java.io :as io])
  (:use [lein-modules.inheritance :only (inherit)]
        [lein-modules.common      :only (parent with-profiles read-project)]
        [lein-modules.compression :only (compressed-profiles)]))

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

(defn id
  "Returns fully-qualified symbol identifier for project"
  [project]
  (if project
    (symbol (:group project) (:name project))))

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

(defn cli-with-profiles
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

(defn dump-modules
  [modules]
  (if (empty? modules)
    (println "No modules found")
    (do
      (println " Module build order:")
      (doseq [p modules]
        (println "  " (:name p))))))

(defn -modules
  "Helper method for main modules task.  This does all the Heavy lifting."
  [project module-list & args]
  (condp = (first args)
      ":checkouts" (do
                     (checkout-dependencies project)
                     (apply -modules project module-list (remove #{":checkouts"} args)))
      ":dirs" (let [dirs (s/split (second args) #"[:,]")]
                (apply -modules
                       (-> project
                           (assoc-in [:modules :dirs] dirs)
                           (vary-meta assoc-in [:without-profiles :modules :dirs] dirs))
                       module-list
                       (drop 2 args)))
      ":sub-module" (let [top-module-name (second args)
                          all-ordered-modules (ordered-builds project)
                          top-module (first (filter #(= top-module-name (:name %)) all-ordered-modules))              ; Top Submodule to build. A map.
                          all-project-names (map id all-ordered-modules)        ; All the names to check against if we care about them.
                          to-build (loop [project-list [(id top-module)]   ; Get a list of all project names that need to be built.
                                          modules-to-build #{}]
                                     (cond (empty? project-list) modules-to-build
                                           :else
                                           (let [sub-module-name (first project-list)
                                                 sub-module (first (filter #(= sub-module-name (id %)) all-ordered-modules))
                                                 all-sub-module-deps (map #(first %) (:dependencies sub-module))
                                                 sub-deps (filter (set all-sub-module-deps) all-project-names) ]
                                             (recur (distinct (concat (rest project-list) sub-deps )) (conj modules-to-build sub-module) ))))]
                      (apply -modules project (filter to-build all-ordered-modules) (drop 2 args)))
      nil (dump-modules module-list)
      (cond (empty? module-list) (apply -modules project (ordered-builds project) args)
                :else
                (let [profiles (compressed-profiles project)
                      args (cli-with-profiles profiles args)
                      subprocess (get-in project [:modules :subprocess]
                                         (or (System/getenv "LEIN_CMD")
                                             (if (= :windows (utils/get-os)) "lein.bat" "lein")))]
                  (dump-modules module-list)
                  (doseq [project module-list]
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
                        (main/apply-task task project (rest args))))))))
  )

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
will override the [:modules :dirs] config in project.clj

And you can choose to build a sub-tree of the project with only its
dependencies in your stack:

  $ lein modules :sub-module web test

This will remove all projects that are not needed by the sub-module
from the build only building modules that it depends on (and they
depend on).
"
  [project & args]
  (apply -modules project () args))
