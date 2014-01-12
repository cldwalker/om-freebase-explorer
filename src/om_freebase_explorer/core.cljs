(ns om-freebase-explorer.core
    (:require-macros [cljs.core.async.macros :refer [go alt!]])
    (:require [goog.events :as events]
              [cljs.core.async :refer [put! <! >! chan timeout]]
              [om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true]
              [goog.net.Jsonp]
              [clojure.string :as string]
              [om-freebase-explorer.utils :refer [guid]]))

;; Lets you do (prn "stuff") to the console
(enable-console-print!)

(def app-state
  (atom {}))

(def search-url "https://www.googleapis.com/freebase/v1/search")

(defn jsonp [url params]
  (let [out (chan 10)]
    (.send (goog.net.Jsonp. url)
           params
           (fn [data] (put! out data))
           (fn [error] (.log js/console "Jsonp ERROR:" error)))
    out))

(defn fetch-search-results []
  (jsonp search-url #js {:query "physics"}))

(defn om-freebase-explorer-app [app owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (go
       (let [data (<! (fetch-search-results))]
         (def dt data)
         (.log js/console "DATA" data)
         (om/set-state! owner :search-results (.-result data)))))
    om/IRender
      (render [_]
        (dom/div nil
                 (dom/h1 nil "Welcome to Freebase Explorer!")
                 (dom/div
                  #js {:id "search_results"}
                  (if-let [result (om/get-state owner :search-results)]
                    (->> (js->clj result :keywordize-keys true)
                           (map :name)
                           (string/join ", "))
                    "No search results"))))))

(om/root app-state om-freebase-explorer-app (.getElementById js/document "app"))
