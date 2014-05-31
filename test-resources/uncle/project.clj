(defproject uncle "0.1.0-SNAPSHOT"
  :description "uncle"

  :profiles {:dev {:foo [:dev]}
             :provided {:foo [:provided]}
             :dist {:foo [:dist]}
             :weirdo {:modules {:dirs ["." "../grandparent/parent/sibling"]}}})
