(defproject vendor "0.1.0-SNAPSHOT"
  :description "3 drinks. 50 cents each."
  :dependencies [[compojure "1.6.1"]
                 [http-kit "2.2.0"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/data.json "0.2.6"]
                 [ring/ring-json "0.4.0"]]
  :main vendor.core
  :aot :all)
