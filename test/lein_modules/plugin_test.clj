(ns lein-modules.plugin-test
  (:use clojure.test
        lein-modules.plugin)
  (:require [leiningen.core.project :as prj]))

(deftest unmerge-should-retain-versions
  (let [p (-> (prj/read "test-resources/lambda/project.clj")
            (prj/unmerge-profiles [:default]))]
    (are [d expected] (= expected (-> p :dependencies (nth d)))
         0 '[cheshire/cheshire "5.2.0"]
         1 '[org.clojure/clojure "1.5.1"]
         2 '[com.taoensso/timbre "3.1.6"])))

(deftest version-keyword-should-override-group-symbol
  (let [p (-> (prj/read "test-resources/grandparent/parent/child/project.clj")
            (update-in [:middleware] conj 'lein-modules.plugin/middleware)
            (vary-meta update-in [:without-profiles :middleware] conj 'lein-modules.plugin/middleware))
        deps (-> p prj/init-project :dependencies set)]
    (is (deps '[foo/d "2.0"]))))
