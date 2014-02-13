(ns lein-modules.inheritance-test
  (:use clojure.test
        lein-modules.inheritance
        [lein-modules.common :only (parent)])
  (:require [leiningen.core.project :as prj]))

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

(deftest compositization
  (let [pp {:name "pp"}
        p {:name "p"
           :modules {:parent pp}
           :profiles {:test {:a 1}}}
        c {:name "c"
           :modules {:parent p}
           :profiles {:test {:a 2}}}]
    (is nil? (compositize-profiles nil))
    (is nil? (compositize-profiles pp))
    (is (= {:test [:test-p] :test-p {:a 1}}
          (compositize-profiles p)))
    (is (= {:test [:test-p :test-c] :test-p {:a 1} :test-c {:a 2}}
          (compositize-profiles c)))))

(deftest profile-application
  (let [child (inherit project)
        base (prj/unmerge-profiles child [:default])]
    (is (= [:inherited] (:foo base)))
    (is (= [:inherited :dev] (:foo (prj/merge-profiles base [:dev]))))
    (is (= [:inherited :provided] (:foo (prj/merge-profiles base [:provided]))))
    (is (= [:inherited :provided :dev] (:foo (prj/merge-profiles base [:default])))))
  (let [top (inherit (prj/read "test-resources/grandparent/project.clj"))
        base (prj/unmerge-profiles top [:default])]
    (is (= [:root :inherited] (:foo base)))
    (is (= [:root :inherited :provided :dev] (:foo (prj/merge-profiles base [:default]))))))
