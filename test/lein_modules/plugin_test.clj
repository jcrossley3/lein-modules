(ns lein-modules.plugin-test
  (:use clojure.test
        lein-modules.plugin
        [leiningen.modules :only (parent)])
  (:require [leiningen.core.project :as prj]
            [clojure.java.io        :as io]))

(def project (prj/read "test-resources/grandparent/parent/child/project.clj"))

(deftest scalar-override
  (let [dad (parent project)
        grandpa (parent dad)]
    (is (false? (-> grandpa :modules :inherited :omit-source)))
    (is (true? (-> dad :modules :inherited :omit-source)))
    (is (nil? (-> project :omit-source)))
    (is (true? (-> (inherit project) :omit-source)))))

(deftest ancestors-sort-oldest-to-youngest
  (let [[grandpa dad] (inherited-profiles project)]
    (is (false? (:omit-source grandpa)))
    (is (true? (:omit-source dad)))))

(deftest paths-resolved-for-child
  (is (nil? (:java-source-paths project)))
  (is (.startsWith (-> (inherit project) :java-source-paths first) (:root project))))

(deftest versionization
  (let [child (versionize project)]
    (is (= "3.0" (-> child :parent second)))
    (is (= '[x/x "1.1.1"] (-> child :dependencies (nth 0))))
    (is (= '[y/y "1.0.2"] (-> child :dependencies (nth 1))))
    (is (= '[scope/scope "9.9.9" :scope "pom"] (-> child :dependencies (nth 2))))
    (is (= '[z/z "1.2.3"] (-> child :dependencies (nth 3))))))

(deftest middleware-only-if-config-present
  (is (not (identical? project (middleware project))))
  (let [uncle (prj/read "test-resources/grandparent/uncle/project.clj")]
    (is (identical? uncle (middleware uncle)))))
