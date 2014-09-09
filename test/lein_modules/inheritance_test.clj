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
        p (with-meta {:name "p"
                      :modules {:parent pp}}
            {:profiles {:test {:a 1}}})
        c (with-meta {:name "c"
                      :modules {:parent p}}
            {:profiles {:test {:a 2}}})]
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

(deftest inherit-should-not-create-redundant-deps
  ;; 'lein deps :tree' will bomb if redundant deps exist and because
  ;; :base includes a non-qualified clojure-complete, multiple :base
  ;; profiles bring in multiple clojure-complete deps
  (let [p (inherit project)]
    (is (= (:dependencies p) (-> p :dependencies distinct)))))

(deftest active-profiles-should-be-applied-to-all-parents
  (let [p (-> (prj/read "test-resources/grandparent/parent/stepchild/project.clj")
            prj/init-project
            (prj/merge-profiles [:by-child :skip-parent]))]
    (is (= "baz" (:foo p)))))

(deftest inheritance-should-work-without-config
  (let [p (-> (prj/read "test-resources/configless/kidB/project.clj")
            prj/init-project)]
    (is (= [:dev] (:foo p)))
    (let [dep (-> p :dependencies first)]
      (is (= 'kidA/kidA (first dep)))
      (is (= "0.1.0-SNAPSHOT" (last dep))))))
