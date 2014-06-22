(defproject stepchild "0.1.0-SNAPSHOT"
  :description "stepchild"
  :modules {:parent nil}
  :middleware [lein-modules.plugin/middleware]
  :profiles {:by-child {:modules {:parent ".."}}})
