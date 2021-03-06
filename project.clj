(defproject lodr "0.1.0-SNAPSHOT"
  :description "Clojure stuff - ElasticSearch, Liberator, Om"
  :url "https://github.com/shanewilson/lodr"
  :license {:name "Apache License v2.0"
            :url "  http://www.apache.org/licenses/"}
  :plugins [[lein-ring "0.8.11"]]
  :ring {:handler lodr.core/handler}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [liberator "0.12.1"]
                 [compojure "1.1.8"]
                 [clojurewerkz/elastisch "2.1.0-beta4"]
                 [prone "0.6.0"]
                 [prismatic/schema "0.2.6"]
                 [instaparse "1.3.3"]
                 [rhizome "0.2.1"]
                 [ring/ring-core "1.3.1"]])
