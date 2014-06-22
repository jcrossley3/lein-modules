(defproject stepmom "0.1.0-SNAPSHOT"
  :description "stepmom"
  :modules {:dirs ["../grandparent/parent/child" "../grandparent/parent/sibling"]
            :inherited {:foo "bar"}
            :versions  {:v   "1"}}

  :profiles {:skip-parent {:modules {:inherited {:foo "baz"}
                                     :versions  {:v   "2"}}}})
