(ns lein-modules.plugin-test
  (:use clojure.test
        lein-modules.plugin)
  (:require [leiningen.core.project :as prj]
            [clojure.java.io        :as io]))

(def project (prj/read "test-resources/grandparent/parent/child/project.clj"))

(deftest parent-project-has-correct-root
  (let [parent (parent project)
        proot (into [] (.split (:root parent) java.io.File/separator))
        croot (into [] (.split (:root project) java.io.File/separator))]
    (is (= proot (butlast croot)))))

(deftest scalar-override
  (let [dad (parent project)
        grandpa (parent dad)]
    (is (false? (-> grandpa :profiles :inherited :omit-source)))
    (is (true? (-> dad :profiles :inherited :omit-source)))
    (is (nil? (-> project :omit-source)))
    (is (true? (-> (inherit project) :omit-source)))))

(deftest ancestors-sort-oldest-to-youngest
  (let [[grandpa dad] (inherited-profiles project)]
    (is (false? (:omit-source grandpa)))
    (is (true? (:omit-source dad)))))

(deftest paths-resolved-for-child
  (is (nil? (:java-source-paths project)))
  (is (.startsWith (-> (inherit project) :java-source-paths first) (:root project))))

