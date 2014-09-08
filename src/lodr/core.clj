(ns lodr.core
  (:import [clojure.lang Keyword])
  (:require [liberator.core :refer [resource defresource]]
            [liberator.dev :refer [wrap-trace]]
            [ring.middleware.params :refer [wrap-params]]
            [clojurewerkz.elastisch.native :as es]
            [clojurewerkz.elastisch.native.document :as esd]
            [clojurewerkz.elastisch.query :as q]
            [clojurewerkz.elastisch.native.response :as esrsp]
            [compojure.core :refer [defroutes ANY]]
            [prone.middleware :as prone]
            [prone.debug :refer [debug]]
            [schema.core :as s]
            [schema.macros :as sm]
            [schema.coerce :as coerce]))

(def conn (es/connect [["127.0.0.1" 9300]]
                      {"cluster.name" "elasticsearch_swilson"}))

(sm/defrecord FilterParams
              [participant :- {s/Keyword s/Str}
               sample :- {s/Keyword s/Str}
               portion :- {s/Keyword s/Str}
               analyte :- {s/Keyword s/Str}
               aliquot :- {s/Keyword s/Str}
               files :- {s/Keyword s/Str}])

(sm/defrecord QueryParams
              [sort :- s/Keyword
               order :- (s/enum :asc :desc)
               from :- s/Int
               size :- s/Int
               fields :- [s/Str]
               filters :- FilterParams
               ])

(defn group-fields [xs v]
  (let [field (keyword (first xs))
        rst (rest xs)]
    (if (empty? rst)
      {field v}
      {field (group-fields rst v)})))

(defn parse-nested-fields [obj [k v]]
  (let [xs (clojure.string/split (name k) #"\.")]
    (merge-with merge obj (group-fields (rest xs) v))))

(defn group-filters-by [field data]
  (let [f-data (filterv (fn [[a _]] (.startsWith (name a) (name field))) data)]
    (reduce parse-nested-fields {} f-data)))

(def query-params-coercer
  (coerce/coercer QueryParams coerce/json-coercion-matcher))

(def filter-coercer
  (coerce/coercer FilterParams coerce/json-coercion-matcher))

(def field->path
  {
    :diseaseCode :admin.disease_code
    :projectCode :admin.project_code
    })

(defn get-filters [params]
  (let [filters {:participant (group-filters-by :participant params)
                 :sample      (group-filters-by :sample params)
                 :portion     (group-filters-by :portion params)
                 :analyte     (group-filters-by :analyte params)
                 :aliquot     (group-filters-by :aliquot params)
                 :files       (group-filters-by :files params)}]
    (debug)
    (filter-coercer (map->FilterParams filters))))

(defn parse-query-params [params]
  (let [params (clojure.walk/keywordize-keys params)
        sort (field->path (keyword (:sort params "diseaseCode")))
        order (:order params "desc")
        from (:from params 0)
        size (:size params 1)
        field (:field params [])
        fields (if (vector? field) field [field])
        filters (get-filters params)]

    (query-params-coercer (QueryParams. sort order from size fields filters))))


(defresource files-resource
             :available-media-types ["application/json"]
             :allowed-methods [:get]
             :handle-ok (fn [ctx]
                          (let [params (parse-query-params (get-in ctx [:request :params]))
                                res (esd/search conn "prototype" "participant"
                                                ;:sort {sort order}
                                                ;:from from
                                                ;:size size
                                                )
                                hits (esrsp/hits-from res)]

                            (debug)
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
