(ns lein-modules.plugin-test
  (:use clojure.test
        lein-modules.plugin)
  (:require [leiningen.core.project :as prj]))

(deftest middleware-only-if-config-present
  (let [child (prj/read "test-resources/grandparent/parent/child/project.clj")]
    (is (not (identical? child (middleware child)))))
  (let [uncle (prj/read "test-resources/grandparent/uncle/project.clj")]
    (is (identical? uncle (middleware uncle)))))
