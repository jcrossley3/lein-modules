(defproject kidB "0.1.0-SNAPSHOT"
  :description "dependent of A"
  :middleware [lein-modules.plugin/middleware]
  :dependencies [[kidA :version]])
