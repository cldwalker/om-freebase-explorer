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

(defn jsonp [out url params]
  (let [x :a]
    (.send (goog.net.Jsonp. url)
           params
           (fn [data] (put! out [:search-result data]))
           (fn [error] (.log js/console "Jsonp ERROR:" error)))
    out))

(defn fetch-search-results [out query]
  (jsonp out search-url #js {:query query}))

(defn search-form [app owner {:keys [chan]}]
  (om/component
   (dom/div nil
            (dom/input
             #js {:id "search_term"})
            (dom/button
             #js {:onClick
                  (fn [e]
                    (put! chan [:search
                                (-> (.querySelector js/document "#search_term")
                                    .-value)]))}
             "Search"))))

(defn om-freebase-explorer-app [app owner]
  (let [search-ch (chan)]
    (reify
      om/IWillMount
      (will-mount [_]
                  (go (while true
                        (.log js/console "Loop...")
                        (let [[event event-data](<! search-ch)]
                          (.log js/console "Event: " event event-data)
                          (case
                            event
                            :search (fetch-search-results search-ch event-data)
                            :search-result (om/set-state! owner :search-results (.-result event-data))
                            (.log js/console "No event found for" event event-data))
                          )))
                  )
      om/IRender
      (render [_]
              (dom/div nil
                       (dom/h1 nil "Welcome to Freebase Explorer!")
                       (om/build search-form app {:opts {:chan search-ch}})
                       (dom/div
                        #js {:id "search_results"}
                        (if-let [result (om/get-state owner :search-results)]
                          (->> (js->clj result :keywordize-keys true)
                               (map :name)
                               (string/join ", "))
                          "No search results")))))))

(om/root app-state om-freebase-explorer-app (.getElementById js/document "app"))
