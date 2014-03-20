(def lambda-version "0.1.0-SNAPSHOT")

(defproject lambda-common-module lambda-version
  :description ""
  :parent [lambda-clj _ :relative-path "../pom.xml"]
  :dependencies [[org.apache.thrift/libthrift "0.9.0"]]
  :thrift-source-path "build-support/thrift"
  :thrift-java-path "src/java"
  :thrift-opts "beans,hashcode")
