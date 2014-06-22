(defproject parent "0.1.0-SNAPSHOT"
  :description "parent"
  :url "http://example.com/parent"
  :parent [grandparent _ :relative-path ".."]
  :modules {:inherited {:java-source-paths ["src/main/java"]
                        :omit-source true
                        :dependencies [[org.clojure/clojure "1.5.1"]]}
            :versions {:ver "2.0"
                       x/x  "1.1.1"
                       a "1"
                       foo "2"
                       c "1"}}
  :profiles {:by-parent {:modules {:dirs ["sibling"]}}
             :by-child  {:modules {:parent "../../stepmom/project.clj"}}})
