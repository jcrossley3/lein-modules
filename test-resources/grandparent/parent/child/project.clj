(defproject child "0.1.0-SNAPSHOT"
  :description "child"
  :parent [parent :ver :relative-path "../pom.xml"]
  :dependencies [[x :x]
                 [y :y]
                 [scope :scope :scope "pom"]]
  :modules {:versions {:ver "3.0"}})
