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
            [prone.debug :refer [debug]]
            [schema.core :as s]
            [schema.macros :as sm]
            [schema.coerce :as coerce]
            [instaparse.core :as insta]
            ))

(def conn (es/connect [["127.0.0.1" 9300]]
                      {"cluster.name" "elasticsearch_swilson"}))

(def query-parser
  (insta/parser
     "expr = s-exp
     <s-exp> = or / and / is-not
     <parens> = <'('> s-exp <')'>
     or = s-exp <'OR'> is-not
     and = s-exp <'AND'> is-not
     <is-not> = is | not | parens
     is = field <'IS'> value
     not = field <'IS NOT'> value
     (* fields cannot be free text *)
     field = word
     (* values can be a term or a list of terms separated by OR*)
     value = terms | term
     (* terms must have at least term OR term*)
     <terms> = (term <'OR'>)+ term
     (* term can be free text or a word *)
     <term> = text | word
     (* free text can support spaces but must be quoted *)
     <text> = <'\"'> #'[A-Za-z0-9-_ ]+' <'\"'>
     <word> = #'[A-Za-z0-9-_.]+'"
    :string-ci true
    ;:output-format :enlive
    :auto-whitespace :standard))

(def transform-options
  {:number read-string
   :vector (comp vec list)
   :expr identity})

(defn parse [input]
  (->> (query-parser input) (insta/transform transform-options)))

;(def AnnotationFilter
;  {(s/optional-key :one)  (s/either s/Str [s/Str])
;   (s/optional-key :two) s/Str})
;
;(def SampleFilter
;  {(s/optional-key :id)         s/Str
;   (s/optional-key :code)       s/Str
;   (s/optional-key :annotation) AnnotationFilter})

(sm/defrecord AnnotationFilter
              [one :- (s/either s/Str [s/Str])
               two :- (s/either s/Str [s/Str])])

(sm/defrecord SampleFilter
              [id :- s/Str
               code :- s/Str
               ;miss :- s/Str
               annotation :- AnnotationFilter])

(sm/defrecord FilterParams
              [participant :- {s/Keyword s/Str}
               sample :- SampleFilter
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
  (let [f-data (filter (fn [[a _]] (.startsWith (name a) (name field))) data)]
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

(defn get-sample-params [params]
  (let [grouped (group-filters-by :sample params)
        annotated (conj grouped {:annotation (map->AnnotationFilter (:annotation grouped))})]
    (map->SampleFilter annotated)))

(defn get-filters [params]
  (let [filters {:participant (group-filters-by :participant params)
                 :sample      (get-sample-params params)
                 :portion     (group-filters-by :portion params)
                 :analyte     (group-filters-by :analyte params)
                 :aliquot     (group-filters-by :aliquot params)
                 :files       (group-filters-by :files params)}
        thing (filter-coercer (map->FilterParams filters))]
    (debug)
    thing))

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
