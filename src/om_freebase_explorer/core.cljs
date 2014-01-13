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
  (.send (goog.net.Jsonp. url)
           params
           (fn [data] (put! out [:search-result data]))
           (fn [error] (.log js/console "Jsonp ERROR:" error))))

(defn fetch-search-results [out query]
  (jsonp out search-url #js {:query query}))

(defn submit-search [chan e]
  (put! chan [:search
              (-> (.querySelector js/document "#search_term")
                  .-value)])
  false)

(defn search-form [app owner {:keys [chan]}]
  (om/component
   (dom/form #js {:onSubmit #(submit-search chan %) }
            (dom/input #js {:type "text" :id "search_term"})
            (dom/input #js {:type "submit" :value "Search"}))))

(defn handle-event [event event-data {:keys [chan owner]}]
  (.log js/console "Event: " event event-data)
  (case event
    :search (fetch-search-results chan event-data)
    :search-result (om/set-state! owner :search-results (.-result event-data))
    (.log js/console "No event found for" event event-data)))

(defn om-freebase-explorer-app [app owner]
  (reify
      om/IWillMount
      (will-mount [_]
                  (let [main-chan (chan 10)]
                    (om/set-state! owner :chan main-chan)
                    (go (while true
                          (let [[event event-data](<! main-chan)]
                            (handle-event
                             event
                             event-data
                             {:chan main-chan :owner owner}))))))
      om/IRender
      (render [_]
              (dom/div nil
                       (dom/h1 nil "Welcome to Freebase Explorer!")
                       (om/build search-form app {:opts {:chan (om/get-state owner :chan)}})
                       (dom/div
                        #js {:id "search_results"}
                        (if-let [result (om/get-state owner :search-results)]
                          (->> (js->clj result :keywordize-keys true)
                               (map :name)
                               (string/join ", "))
                          "No search results"))))))

(om/root app-state om-freebase-explorer-app (.getElementById js/document "app"))
