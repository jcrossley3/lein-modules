(defproject child "0.1.0-SNAPSHOT"
  :description "child"
  :parent [parent _ :relative-path "../pom.xml"]
  :dependencies [[x _]
                 [y _]
                 [scope _ :scope "pom"]
                 [z "1.2.3"]
                 [foo/a _]
                 [foo/b _]
                 [foo/c _]
                 [foo/d :ver]
                 [sibling _]]
  :modules {:versions {parent "3.0"}}
  :profiles {:by-child  {}})
