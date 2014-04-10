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
  (let [m (compositize-profiles project)]
    (is (false? (-> m :inherited-grandparent :omit-source)))
    (is (true? (-> m :inherited-parent :omit-source)))))

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

(deftest profile-application-child
  (let [p (inherit project)]
    (is (= [:inherited :provided :dev] (:foo p)))
    (is (= [:inherited :provided :dev :dist] (:foo (prj/merge-profiles p [:dist]))))
    (is (= [:inherited :provided :dev :dist] (:foo (prj/set-profiles p [:inherited :default :dist]))))
    (is (= [:inherited :dist] (:foo (-> p (prj/unmerge-profiles [:default]) (prj/merge-profiles [:dist])))))))

(deftest profile-application-base
  (let [p (inherit (prj/read "test-resources/grandparent/project.clj"))]
    (is (= [:root :inherited :provided :dev] (:foo p)))
    (is (= [:root :inherited :provided :dev :dist] (:foo (prj/merge-profiles p [:dist]))))
    (is (= [:root :inherited :provided :dev :dist] (:foo (prj/set-profiles p [:inherited :default :dist]))))
    (is (= [:root :inherited :dist] (:foo (-> p (prj/unmerge-profiles [:default]) (prj/merge-profiles [:dist])))))))

(deftest profile-application-uninherited
  (let [p (inherit (prj/read "test-resources/uncle/project.clj"))]
    (is (= [:provided :dev] (:foo p)))
    (is (= [:provided :dev :dist] (:foo (prj/merge-profiles p [:dist]))))
    (is (= [:provided :dev :dist] (:foo (prj/set-profiles p [:default :dist]))))
    (is (= [:dist] (:foo (-> p (prj/unmerge-profiles [:default]) (prj/merge-profiles [:dist])))))))
