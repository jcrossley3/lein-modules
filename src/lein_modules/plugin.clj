(ns lein-modules.plugin
  (:use [lein-modules.versionization :only (versionize)]
        [lein-modules.inheritance    :only (inherit)]
        [lein-modules.common         :only (config)]))

(defn middleware
  "Implicit Leiningen middleware"
  [project]
  (if (-> project config empty?)
    project
    (-> project inherit versionize)))
