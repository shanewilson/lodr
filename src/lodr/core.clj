(ns lodr.core
  (:require [liberator.core :refer [resource defresource]]
            [liberator.dev :refer [wrap-trace]]
            [ring.middleware.params :refer [wrap-params]]
            [clojurewerkz.elastisch.native :as es]
            [clojurewerkz.elastisch.native.document :as esd]
            [clojurewerkz.elastisch.query :as q]
            [compojure.core :refer [defroutes ANY]]))

(def conn (es/connect  [["127.0.0.1" 9300]]
                   {"cluster.name" "elasticsearch_swilson"}))

(defresource files-resource
             :available-media-types ["application/json"]
             :allowed-methods [:get]
             :handle-ok (fn [_]
                          (let [res (esd/search conn "prototype" "participant")
                                hits (-> res :hits :hits )]
                            (map #(:_source %) hits))))

(defresource file-resource [id]
             :available-media-types ["application/json"]
             :allowed-methods [:get]
             :exists? (fn [_]
                        (let [res (esd/get conn "prototype" "participant" id)]
                          (if (true? (:exists res))
                            {::file (:_source res)})))
             :handle-ok ::file)

(defroutes app
           (ANY "/files" [] files-resource)
           (ANY "/files/:id" [id] (file-resource id)))

(def handler
  (-> app
      wrap-params
      ;(wrap-trace :header :ui)
      ))
