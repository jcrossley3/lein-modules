(ns lein-modules.plugin-test
  (:use clojure.test
        lein-modules.plugin)
  (:require [leiningen.core.project :as prj]))

(deftest middleware-only-if-config-present
  (let [child (prj/read "test-resources/grandparent/parent/child/project.clj")]
    (is (not (identical? child (middleware child)))))
  (let [uncle (prj/read "test-resources/uncle/project.clj")]
    (is (identical? uncle (middleware uncle)))))

(deftest unmerge-should-retain-versions
  (let [p (-> (prj/read "test-resources/lambda/project.clj")
            middleware
            (prj/unmerge-profiles [:default]))]
    (are [d expected] (= expected (-> p :dependencies (nth d)))
         0 '[cheshire/cheshire "5.2.0"]
         1 '[org.clojure/clojure "1.5.1"]
         2 '[com.taoensso/timbre "3.1.6"])))
