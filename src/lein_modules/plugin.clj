(ns lein-modules.plugin
  (:use [lein-modules.versionization :only (versionize)]
        [lein-modules.inheritance    :only (inherit)]
        [lein-modules.common         :only (with-middleware)]))

(defn middleware
  "Implicit Leiningen middleware, guarding recursive
  middleware calls with a metadata flag.
  See https://github.com/technomancy/leiningen/issues/1151"
  [project]
  (with-middleware project
    (-> project
      inherit
      versionize)))

