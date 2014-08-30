(ns lodr.core
  (:require [liberator.core :refer [resource defresource]]
            [liberator.dev :refer [wrap-trace]]
            [ring.middleware.params :refer [wrap-params]]
            [compojure.core :refer [defroutes ANY]]))

(def dbg-counter (atom 0))

(defresource dbg-resource
             :available-media-types ["text/plain"]
             :allowed-methods [:get :post]
             :handle-ok (fn [_] (format "The counter is %d" @dbg-counter))
             :post! (fn [_] (swap! dbg-counter inc)))

(defroutes app
           (ANY "/changetag" []
                (resource
                  :available-media-types ["text/plain"]
                  ;; etag changes every 10s
                  :etag (let [i (int (mod (/ (System/currentTimeMillis) 10000) 10))]
                          (.substring "abcdefhghijklmnopqrst"  i (+ i 10)))
                  :handle-ok (format "It's now %s" (java.util.Date.))))
           (ANY "/timehop" []
                (resource
                  :available-media-types ["text/plain"]
                  ;; timestamp changes every 10s
                  :last-modified (* 10000 (long  (/ (System/currentTimeMillis) 10000)))
                  :handle-ok (fn [_] (format "It's now %s" (java.util.Date.)))))
           (ANY "/babel" []
                (resource :available-media-types ["text/plain" "text/html"
                                                  "application/json" "application/clojure;q=0.9"]
                          :handle-ok
                          #(let [media-type
                                 (get-in % [:representation :media-type])]
                            (condp = media-type
                              "text/plain" "You requested plain text"
                              "text/html" "<html><h1>You requested HTML</h1></html>"
                              {:message "You requested a media type"
                               :media-type media-type}))
                          :handle-not-acceptable "Uh, Oh, I cannot speak those languages!"))
           (ANY "/dbg-count" [] dbg-resource)
           (ANY "/choice" []
                (resource :available-media-types ["text/html"]
                          :exists? (fn [ctx]
                                     (if-let [choice
                                              (get {"1" "stone" "2" "paper" "3" "scissors"}
                                                   (get-in ctx [:request :params "choice"]))]
                                       {:choice choice}))
                          :handle-ok (fn [ctx]
                                       (format "<html>Your choice: &quot;%s&quot;."
                                               (get ctx :choice)))
                          :handle-not-found (fn [ctx]
                                              (format "<html>There is no value for the option &quot;%s&quot;"
                                                      (get-in ctx [:request :params "choice"] "")))))
           (ANY "/secret" []
                (resource :available-media-types ["text/html"]
                          :exists? (fn [ctx]
                                     (= "tiger" (get-in ctx [:request :params "word"])))
                          :handle-ok "You found the secret word!"
                          :handle-not-found "Uh, that's the wrong word. Guess again!"))
           (ANY "/bar/:txt" [txt] (parameter txt))
           (ANY "/foo" []
                (resource :available-media-types ["text/html"]
                          :handle-ok (fn [ctx]
                                       (format "<html>It's %d milliseconds since the beginning of the epoch."
                                               (System/currentTimeMillis))))))

(def handler
  (-> app
      wrap-params
      ;(wrap-trace :header :ui)
      ))
