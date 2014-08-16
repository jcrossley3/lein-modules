(ns lein-modules.compression-test
  (:use clojure.test
        lein-modules.compression)
  (:require [leiningen.core.project :as prj]))

(deftest composite-compression
  (let [profiles {:a [:b :c], :b {}, :c [:d], :d {}}]
    (is (= [:a] (compress [:b :d] profiles)))
    (is (= [:a :k] (compress [:b :d :k] profiles)))
    (is (= [:a :a :k] (compress [:a :b :d :k] profiles)))
    (is (= [:a] (compress [:b :d :d] profiles)))
    (is (= [:k :s :t] (compress [:k :s :t] profiles)))
    (is (= [:k :s :t] (compress [:k :s :t] {})))))

(deftest default-profile-excluded-from-compression
  (let [p (prj/read "test-resources/grandparent/parent/stepchild/project.clj")]
    (is (= [:base :system :user :provided :dev] (compressed-profiles p)))))
