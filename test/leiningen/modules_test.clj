(ns leiningen.modules-test
  (:use clojure.test
        leiningen.modules)
  (:require [leiningen.core.project :as prj]
            [clojure.java.io        :as io]))

(deftest parent-project-has-correct-root
  (let [project (prj/read "test-resources/grandparent/parent/child/project.clj")
        parent (parent project)
        proot (into [] (.split (:root parent) java.io.File/separator))
        croot (into [] (.split (:root project) java.io.File/separator))]
    (is (= proot (butlast croot)))))

(deftest children-checking
  (let [ann     (prj/read "test-resources/grandparent/parent/child/project.clj")
        nancy   (prj/read "test-resources/grandparent/parent/sibling/project.clj")
        flip    (prj/read "test-resources/grandparent/parent/project.clj")
        fiona   (prj/read "test-resources/grandparent/stepmom/project.clj")
        uncle   (prj/read "test-resources/grandparent/uncle/project.clj")
        grandpa (prj/read "test-resources/grandparent/project.clj")]
    (is (child? flip ann))
    (is (child? grandpa flip))
    (is (not (child? grandpa ann)))
    (is (not (child? grandpa uncle)))
    (is (not (child? uncle ann)))
    (is (empty? (children ann)))
    (is (= #{ann nancy} (set (children flip))))
    (is (= #{ann nancy} (set (children fiona))))
    (is (= [flip] (children grandpa)))
    (is (= #{flip ann nancy} (set (progeny grandpa))))))
