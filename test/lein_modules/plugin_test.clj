(ns lein-modules.plugin-test
  (:use clojure.test
        lein-modules.plugin)
  (:require [leiningen.core.project :as prj]))

(deftest middleware-only-if-config-present
  (let [child (prj/read "test-resources/grandparent/parent/child/project.clj")]
    (is (not (identical? child (middleware child)))))
  (let [uncle (prj/read "test-resources/grandparent/uncle/project.clj")]
    (is (identical? uncle (middleware uncle)))))

(deftest unmerge-should-retain-versions
  (let [p (middleware (prj/read "test-resources/grandparent/parent/child/project.clj"))]
    (is (= '[x/x "1.1.1"] (-> p (prj/unmerge-profiles [:default]) :dependencies first)))))
