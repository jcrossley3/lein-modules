(def version "0.1.0-SNAPSHOT")

(defproject lambda-clj version
  :description ""
  :packaging "pom"
  :profiles {:dev {:dependencies [[midje/midje _]]}}
  :dependencies [[cheshire _]]
  :modules {:inherited
            {:source-paths ["src/clj"]
             :java-source-paths ["src/java"]
             :test-paths ["test/clj" "test/java"]
             :dependencies [[org.clojure/clojure _]
                            [com.taoensso/timbre _]]}

            :versions {org.clojure/clojure "1.5.1"
                       midje/midje "1.6.3"
                       com.taoensso/timbre "3.1.6"
                       cheshire "5.2.0"
                       :lambda-clj "0.1.0-SNAPSHOT"
                       
                       lambda-common-module :lambda-clj
                       lambda-clj :lambda-clj}}

  :codox {:sources ["lambda-common/src"
                    "lambda-etl/src"]})
