(defproject lein-modules "0.2.1-SNAPSHOT"
  :description "Similar to Maven multi-module projects, but less sucky"
  :url "https://github.com/jcrossley3/lein-modules"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :eval-in-leiningen true
  :signing {:gpg-key "92439EF5"}
  :lein-release {:deploy-via :clojars})
