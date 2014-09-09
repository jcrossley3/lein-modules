(ns leiningen.modules-test
  (:use clojure.test
        leiningen.modules
        [lein-modules.common :only (parent)]
        [lein-modules.inheritance :only (inherit)])
  (:require [leiningen.core.project :as prj]
            [leiningen.clean :refer [delete-file-recursively]]
            [clojure.java.io        :as io]))

(defn rootset
  "Avoid testing all attributes of a project, just the :root"
  [coll]
  (->> coll (map :root) set))

(deftest parent-project-has-correct-root
  (let [project (prj/read "test-resources/grandparent/parent/child/project.clj")
        parent (parent project)
        proot (into [] (.split (:root parent) java.io.File/separator))
        croot (into [] (.split (:root project) java.io.File/separator))]
    (is (= proot (butlast croot)))))

(deftest children-checking
  (let [ann     (prj/read "test-resources/grandparent/parent/child/project.clj")
        nancy   (prj/read "test-resources/grandparent/parent/sibling/project.clj")
        bert    (prj/read "test-resources/grandparent/parent/stepchild/project.clj")
        flip    (prj/read "test-resources/grandparent/parent/project.clj")
        fiona   (prj/read "test-resources/stepmom/project.clj")
        grandpa (prj/read "test-resources/grandparent/project.clj")
        uncle   (prj/read "test-resources/uncle/project.clj")
        weirdo  (prj/set-profiles uncle [:weirdo])]
    (is (child? flip ann))
    (is (not (child? flip bert)))
    (is (child? grandpa flip))
    (is (not (child? grandpa ann)))
    (is (not (child? grandpa uncle)))
    (is (not (child? uncle ann)))
    (is (empty? (children ann)))
    (is (= (rootset [ann nancy]) (rootset (children flip))))
    (is (= (rootset [ann nancy]) (rootset (children fiona))))
    (is (= (rootset [flip]) (rootset (children grandpa))))
    (is (= (rootset [flip ann nancy]) (rootset (vals (progeny grandpa)))))
    (is (= (rootset [uncle nancy]) (rootset (vals (progeny weirdo)))))))

(deftest profiled-children
  (let [p (prj/read "test-resources/grandparent/parent/project.clj")]
    (is (= #{"child" "sibling"}   (set (map :name (vals (progeny p))))))
    (is (= #{"sibling"}           (set (map :name (vals (progeny (prj/set-profiles p [:by-parent])))))))
    (is (= #{"child" "stepchild"} (set (map :name (vals (progeny (prj/set-profiles p [:by-child])))))))))

(deftest profiled-parents
  (let [p (prj/read "test-resources/grandparent/parent/stepchild/project.clj")]
    (is (nil? (parent p)))
    (is (= "parent" (-> (prj/set-profiles p [:by-child]) parent :name)))
    (is (= "stepmom" (-> (prj/set-profiles p [:by-child]) parent parent :name)))))

(deftest build-order
  (let [p (prj/read "test-resources/grandparent/project.clj")]
    (is (= ["parent" "sibling" "child"] (->> p ordered-builds (map :name))))))

(deftest checkouts
  (let [grandpa (io/file "test-resources/grandparent/checkouts")
        dad     (io/file "test-resources/grandparent/parent/checkouts")
        child   (io/file "test-resources/grandparent/parent/child/checkouts")
        sib     (io/file "test-resources/grandparent/parent/sibling/checkouts")]
    (try
      (modules (prj/read "test-resources/grandparent/project.clj") ":checkouts")
      (is (not (.exists grandpa)))
      (is (not (.exists dad)))
      (is (not (.exists sib)))
      (is (.exists child))
      (is (.exists (io/file child "sibling")))
      (finally
        (delete-file-recursively child)))))

(deftest dirs "no sibling among modules so no checkouts"
  (let [dad     (io/file "test-resources/grandparent/parent/checkouts")
        child   (io/file "test-resources/grandparent/parent/child/checkouts")]
    (modules (prj/read "test-resources/grandparent/parent/project.clj")
      ":dirs" "child" ":checkouts")
    (is (not (.exists dad)))
    (is (not (.exists child)))))

(deftest configless-modules
  (let [p (prj/init-project (prj/read "test-resources/configless/project.clj"))]
    (is (= ["kidA" "kidB"] (->> p ordered-builds (map :name))))))
