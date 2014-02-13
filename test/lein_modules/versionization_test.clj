(ns lein-modules.versionization-test
  (:use clojure.test
        lein-modules.versionization)
  (:require [leiningen.core.project :as prj]))

(deftest versionization
  (let [child (versionize (prj/read "test-resources/grandparent/parent/child/project.clj"))]
    (is (= "3.0" (-> child :parent second)))
    (is (= '[x/x "1.1.1"] (-> child :dependencies (nth 0))))
    (is (= '[y/y "1.0.2"] (-> child :dependencies (nth 1))))
    (is (= '[scope/scope "9.9.9" :scope "pom"] (-> child :dependencies (nth 2))))
    (is (= '[z/z "1.2.3"] (-> child :dependencies (nth 3))))))
