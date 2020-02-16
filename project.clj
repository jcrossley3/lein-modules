(defproject com.skipgear/lein-multi-modules "0.4.0"
  :description "Fork of lein-modules. https://github.com/jcrossley3/lein-modules"
  :url "https://github.com/ruped/lein-multi-modules"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :eval-in-leiningen true
  :aliases {"all" ["do" "clean," "test," "install"]}
  :plugins [[lein-file-replace "0.1.0"]]
  :deploy-repositories [["clojars"   {:sign-releases false :url "https://clojars.org/repo"}]
                        ["releases"  {:sign-releases false :url "https://clojars.org/repo"}]
                        ["snapshots" {:sign-releases false :url "https://clojars.org/repo"}]])
