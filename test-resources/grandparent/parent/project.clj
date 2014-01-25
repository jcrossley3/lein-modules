(defproject parent "0.1.0-SNAPSHOT"
  :description "parent"
  :url "http://example.com/parent"
  :parent [grandparent "0.1.0-SNAPSHOT" :relative-path "../pom.xml"]
  :modules {:inherited {:java-source-paths ["src/main/java"]
                        :omit-source true
                        :dependencies [[org.clojure/clojure "1.5.1"]]}})
