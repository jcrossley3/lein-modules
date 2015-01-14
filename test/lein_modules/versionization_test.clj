(ns lein-modules.versionization-test
  (:use clojure.test
        lein-modules.versionization)
  (:require [leiningen.core.project :as prj]))

(deftest versionization
  (let [child (versionize (prj/read "test-resources/grandparent/parent/child/project.clj"))
        deps (-> child :dependencies set)]
    (is (= "3.0" (-> child :parent second)))
    (are [d] (deps d)
         '[x/x "1.1.1"]
         '[y/y "1.0.2"]
         '[scope/scope "9.9.9" :scope "pom"]
         '[z/z "1.2.3"]
         '[foo/a "1"]
         '[foo/b "2"]
         '[foo/c "3"]
         '[foo/d "2.0"])))

(deftest partial-composites-should-work
  (let [p (-> (prj/read "test-resources/lambda/project.clj")
            (prj/set-profiles [:dev]))
        deps (into {} (->> p :dependencies (map (juxt first second))))]
    (are [d expected] (= expected (get deps d))
         'cheshire/cheshire     "5.2.0"
         'org.immutant/immutant "1.1.1"
         'midje/midje           "1.6.3"
         'org.clojure/clojure   "1.5.1"
         'com.taoensso/timbre   "3.1.6")))

(deftest versionization-by-profile
  (let [p (-> (prj/read "test-resources/grandparent/parent/stepchild/project.clj")
            prj/init-project)]
    (is (nil?  (-> p (prj/merge-profiles []) versions :v)))
    (is (= "1" (-> p (prj/merge-profiles [:by-child]) versions :v)))
    (is (= "2" (-> p (prj/merge-profiles [:by-child :skip-parent]) versions :v)))
    (is (= "3" (-> p (prj/merge-profiles [:by-child :skip-parent :version-override]) versions :v)))))
