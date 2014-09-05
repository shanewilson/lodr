(ns lodr.core
  (:require [liberator.core :refer [resource defresource]]
            [liberator.dev :refer [wrap-trace]]
            [ring.middleware.params :refer [wrap-params]]
            [clojurewerkz.elastisch.native :as es]
            [clojurewerkz.elastisch.native.document :as esd]
            [clojurewerkz.elastisch.query :as q]
            [clojurewerkz.elastisch.native.response :as esrsp]
            [compojure.core :refer [defroutes ANY]]
            [prone.middleware :as prone]
            [prone.debug :refer [debug]]))

(def conn (es/connect [["127.0.0.1" 9300]]
                      {"cluster.name" "elasticsearch_swilson"}))

(defresource files-resource
             :available-media-types ["application/json"]
             :allowed-methods [:get]
             :handle-ok (fn [{request :request}]
                          (let [params (:params request)
                                sort (keyword (params "sort" "_score"))
                                order (params "order" "desc")
                                from (Integer/parseInt (params "from" "0") 10)
                                size (Integer/parseInt (params "size" "1") 10)
                                res (esd/search conn "prototype" "participant"
                                                :sort {sort order}
                                                :from from
                                                :size size)
                                hits (esrsp/hits-from res)]
                            ;(debug)
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
      prone/wrap-exceptions
      ;(wrap-trace :header :ui)
      ))
