(ns leiningen.modules
  (:require [leiningen.core.project :as prj]
            [leiningen.core.main :as main]
            [leiningen.core.eval :as eval]
            [leiningen.core.utils :as utils]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.java.shell :as sh]
            [clojure.string :as s])
  (:use [lein-modules.inheritance :only (inherit)]
        [lein-modules.common      :only (parent with-profiles read-project)]
        [lein-modules.compression :only (compressed-profiles)])
  (:import [java.util.concurrent Executors]))


(do
  (ns leiningen.core.eval) ;;monkey patch leiningen
  (defn sh
    [& args]
    (let [{:keys [out err exit]} (apply clojure.java.shell/sh (concat args [:dir *dir*]))]
      (when-not (clojure.string/blank? out)
        (println out))
      (when-not (clojure.string/blank? err)
        (binding [*out* *err*]
          (println err)))
      exit))
  (ns leiningen.modules))

(defmacro with-out-str-and-err
  "Like with-out-str - but also covers err
  Evaluates exprs in a context in which *out* is bound to a fresh
  StringWriter.  Returns the string created by any nested printing
  calls."
  [& body]
  `(let [s# (new java.io.StringWriter)
         e# (new java.io.StringWriter)]
     (binding [*out* s#
               *err* e#]
       (let [val-exception# (try [(do ~@body) nil]
                                 (catch Exception exception# [nil exception#]))]
         {:val (first val-exception#)
          :out (str s#)
          :err (str e#)
          :exception (second val-exception#)}))))

(defmacro approx-time
  "Like clojure.core/time -but approximate
  Evaluates expr and prints the time it took.  Returns the value of
  expr."
  [label expr]
  `(let [start# (. System (nanoTime))
         ret# ~expr]
     (println (str "Elapsed time for " ~label ": "(long (/ (double (- (. System (nanoTime)) start#)) 1000000.0)) " msecs"))
     ret#))



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
         (filter #(= "project.clj" (.getName ^java.io.File %)))
         (remove #(= (:root project) (.getParent ^java.io.File %)))
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
        (throw (Exception. ^String (apply str "Cyclic dependency: " (interpose ", " (map :name (keys deps))))))))))

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

(defn print-modules
  "If running in 'quiet' mode, only prints the located modules.

  Otherwise prints a more human-formatted modules list."

  [{:keys [quiet?]} modules]
  (if (empty? modules)
    (if-not quiet?
      (println "No modules found"))

    ;; There are modules
    (do (if-not quiet?
          (println " Module build order:"))
        (doseq [p modules]
          (if-not quiet?
            (println "  " (:name p))
            (println (:name p))))

        ;; For the test suite, return all children.
        (map id modules))))

(defn exec
  [{:keys [quiet? project args subprocess]}]
  (approx-time (:name project)
               (do
                 (when-not quiet?
                   (println "------------------------------------------------------------------------")
                   (println " Building" (:name project) (:version project) (dump-profiles args))
                   (println "------------------------------------------------------------------------"))
                 (if-let [cmd (get-in project [:modules :subprocess] subprocess)]
                   (binding [eval/*dir* (:root project)]
                     (let [exit-code (apply eval/sh (cons cmd args))]
                       (when (pos? exit-code)
                         (throw (ex-info "Subprocess failed" {:exit-code exit-code})))))
                   (let [project (prj/init-project project)
                         task (main/lookup-alias (first args) project)]
                     (main/apply-task task project (rest args)))))))

(defn run-sequential
  [{:keys [quiet? args subprocess opts project] :as x}]
  (let [modules (ordered-builds project)]
    (when-not quiet?
      (print-modules opts modules))
    (doseq [project modules]
      (exec (assoc x :project project)))))


(defn run-parallel
  [{:keys [quiet? args subprocess opts project done executor] :as x}]
  (let [modules (->> project progeny interdependence)]
    (when-not quiet?
      (print-modules opts (topological-sort modules)))
    (let [a-state (atom {:started #{}
                         :completed #{}})]
      (letfn [(start [project]
                (when project (swap! a-state
                                     #(update %1 :started conj %2) project)))
              (complete [project]
                (when project (swap! a-state
                                     #(update %1 :completed conj %2) project)))
              (proceed [project]
                (locking a-state
                  (let [{:keys [started completed] :as state} @a-state]
                    (when (= (count completed) (count modules))
                      (deliver done true))
                    (let [{ready true waiting false}
                          (->> modules
                               (remove (comp started first))
                               (group-by (fn [[k deps]]
                                           (empty? (set/difference (into #{} deps)
                                                                   completed)))))]
                      (println "Jobs: [ "
                               "Waiting:" (count waiting)
                               "Ready:" (count ready)
                               "Running:" (- (count started) (count completed))
                               "Done:" (count completed) "]")
                      (doseq [[project deps] ready]
                        (start project)
                        (executor
                         (fn []  (try (let [{:keys [val exception out err]}
                                            (with-out-str-and-err
                                              (exec (assoc
                                                     x
                                                     :project project)))]
                                        (locking a-state
                                          (println out)
                                          (when-not (s/blank? err)
                                            (println (str "Err Output (" (:name project) "):"))
                                            (print err))
                                          (complete project)
                                          (proceed project)
                                          (when exception
                                            (println "Failed running: ")
                                            (.printStackTrace exception)
                                            (println "===")
                                            (deliver done false)
                                            (throw exception))))))))))))]
        (proceed nil)))))

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

  Accepts '-q', '--quiet' and ':quiet' to suppress non-subprocess output."
  [project & args]
  (let [[quiet? args] ((juxt some remove) #{"-q" "--quiet" ":quiet"} args)
        [p args] (loop [p nil out-args [] args args]
                   (if (seq args)
                     (if (= "-p" (first args))
                       (recur (Integer/parseInt (second args)) out-args (drop 2 args))
                       (recur p (conj out-args (first args)) (rest args)))
                     [p out-args]))
        quiet? (or quiet? (-> project :modules :quiet))
        {:keys [quiet?] :as opts} {:quiet? (boolean quiet?)}]
    (condp = (first args)
      ":checkouts" (do
                     (checkout-dependencies project)
                     (apply modules project (remove #{":checkouts"} args)))
      ":dirs" (let [dirs (s/split (second args) #"[:,]")]
                (apply modules
                       (-> project
                           (assoc-in [:modules :dirs] dirs)
                           (assoc-in [:modules :quiet] quiet?)
                           (vary-meta assoc-in [:without-profiles :modules :dirs] dirs))
                       (drop 2 args)))
      nil (print-modules opts (ordered-builds project))
      (let [profiles (compressed-profiles project)
            args (cli-with-profiles profiles args)
            subprocess (get-in project [:modules :subprocess]
                               (or (System/getenv "LEIN_CMD")
                                   (if (= :windows (utils/get-os)) "lein.bat" "lein")))
            done (promise)]
        (if p
          (do
            (println "Running in parallel with " p " threads")
            (run-parallel {:quiet? quiet?
                           :args args
                           :subprocess subprocess
                           :opts opts
                           :project project
                           :done done
                           :executor (let [tp (Executors/newFixedThreadPool p)]
                                       (fn [^Callable fx]
                                         (.submit tp fx)))})
            (Thread/sleep 50);;wait for console output
            (System/exit (if @done 0 1)))
          (run-sequential {:quiet? quiet?
                           :args args
                           :subprocess subprocess
                           :opts opts
                           :project project}))))))
