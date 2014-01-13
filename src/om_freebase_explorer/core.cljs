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

(def id-url "https://www.googleapis.com/freebase/v1/topic/%s")

(defn jsonp [chan event url params]
  (.send (goog.net.Jsonp. url)
           params
           (fn [data] (put! chan [event data]))
           (fn [error] (.log js/console "Jsonp ERROR:" error))))

(defn fetch-search-results [chan query]
  (jsonp chan :ui.search search-url #js {:query query}))

(defn fetch-id-results [chan id]
  (jsonp chan :ui.id (string/replace id-url "%s" id) #js {:filter "commons"}))

(defn submit-search [chan e]
  (put! chan [:service.search
              (-> (.querySelector js/document "#search_term")
                  .-value)])
  false)

(defn search-form [app owner {:keys [chan]}]
  (om/component
   (dom/form #js {:onSubmit #(submit-search chan %) }
            (dom/input #js {:type "text" :id "search_term"})
            (dom/input #js {:type "submit" :value "Search"}))))

;; TODO: give each element a :key
(defn render-table [headers rows]
  (apply dom/table nil
         (dom/caption nil (str "Found " (count rows) " results"))
         (apply dom/tr nil
                  (map
                   (fn [value] (dom/th nil value))
                   headers))
         (map #(apply dom/tr
                       nil
                       (map
                        (fn [value] (dom/td nil value))
                        %))
               rows)))

(defn click-id-link [chan id e]
  (.log js/console e)
  (put! chan [:service.id id])
  false)

(defn search-results [app owner {:keys [result chan]}]
  (om/component
   (dom/div
    #js {:id "search_results"}
    (if result
      (do (.log js/console "DATA" result)
        (->> (js->clj result :keywordize-keys true)
             (map #(vector (dom/a #js {:href "#" :onClick (partial click-id-link chan (:id %))}
                                  nil (:id %))
                           (:name %)))
             (render-table ["Id" "Name"])))
      ""))))

;; consider reuse with search-results once this is more fleshed out
(defn id-results [app owner {:keys [result]}]
  (om/component
   (dom/div
    #js {:id "id_results"}
    (if result
      (do (.log js/console "DATA" result)
        (->> (js->clj result :keywordize-keys true)
             (map (fn [[k v]] [k (:count v) (pr-str (:values v))]))
             (render-table ["Id" "Count" "Values"])))
      ""))))

(defn handle-event [event event-data {:keys [chan owner]}]
  (.log js/console "Event: " (pr-str event) event-data)
  (case event
    :service.search (fetch-search-results chan event-data)
    :service.id (fetch-id-results chan event-data)
    :ui.search (om/set-state! owner :search-result (.-result event-data))
    :ui.id (do
                 (om/set-state! owner :search-result nil) ;; temp hack to hide search table
                 (om/set-state! owner :id-result (.-property event-data)))
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
                       ;; Consider not rendering these when they have no results
                       (om/build search-results app {:opts {:result (om/get-state owner :search-result)
                                                            :chan (om/get-state owner :chan)}})
                       (om/build id-results app {:opts {:result (om/get-state owner :id-result)}})))))

(om/root app-state om-freebase-explorer-app (.getElementById js/document "app"))
