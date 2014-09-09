(ns lein-modules.plugin
  (:use [lein-modules.versionization :only (versionize)]
        [lein-modules.inheritance    :only (inherit)]
        [lein-modules.common         :only (config)]))

(defn middleware
  "Implicit Leiningen middleware, guarding recursive
  middleware calls with a metadata flag.
  See https://github.com/technomancy/leiningen/issues/1151"
  [project]
  (if (-> project meta ::middleware-applied)
    project
    (-> project
      (vary-meta assoc ::middleware-applied true)
      inherit
      versionize
      (vary-meta dissoc ::middleware-applied))))
