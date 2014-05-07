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
        uncle   (prj/read "test-resources/uncle/project.clj")
        grandpa (prj/read "test-resources/grandparent/project.clj")]
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
    (is (= (rootset [grandpa flip ann nancy]) (rootset (vals (progeny grandpa)))))))

(deftest build-order
  (let [p (prj/read "test-resources/grandparent/project.clj")]
    (is (= ["grandparent" "parent" "sibling" "child"] (->> p ordered-builds (map :name))))))

(deftest checkouts
  (let [grandpa (io/file "test-resources/grandparent/checkouts")
        dad     (io/file "test-resources/grandparent/parent/checkouts")
        child   (io/file "test-resources/grandparent/parent/child/checkouts")
        sib     (io/file "test-resources/grandparent/parent/sibling/checkouts")]
    (try
      (checkout-dependencies (prj/read "test-resources/grandparent/project.clj"))
      (is (not (.exists grandpa)))
      (is (.exists dad))
      (is (.exists child))
      (is (.exists (io/file child "sibling")))
      (is (.exists (io/file child "parent")))
      (is (.exists sib))
      (finally
        (delete-file-recursively dad)
        (delete-file-recursively child)
        (delete-file-recursively sib)))))
