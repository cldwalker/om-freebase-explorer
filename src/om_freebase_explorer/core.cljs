(ns om-freebase-explorer.core
    (:require-macros [cljs.core.async.macros :refer [go alt!]])
    (:require [goog.events :as events]
              [cljs.core.async :refer [put! <! >! chan timeout]]
              [om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true]
              [om-components.filtered-table :refer [filtered-table]]
              [goog.net.Jsonp]
              [clojure.string :as string]))

;; Lets you do (prn "stuff") to the console
(enable-console-print!)

(def app-state
  (atom {}))

;; app history and undo
;; ====================
(def app-history (atom [@app-state]))

(add-watch app-state :history
  (fn [_ _ _ n]
    (when-not (= (last @app-history) n)
      (swap! app-history conj n))
    (set! (.-innerHTML (.getElementById js/document "message"))
      (let [c (count @app-history)]
        (str c " Saved state(s)")))))

(aset js/window "undo"
  (fn [e]
    (when (> (count @app-history) 1)
      (swap! app-history pop)
      (reset! app-state (last @app-history)))))

;; freebase
;; ========
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

;; UI
;; ==
(defn submit-search [chan e]
  (put! chan [:service.search
              (-> (.querySelector js/document "#search_term")
                  .-value)])
  false)

(defn search-form [app owner {:keys [chan]}]
  (om/component
   (dom/form #js {:onSubmit #(submit-search chan %) }
            (dom/input #js {:type "text" :id "search_term"})
            (dom/input #js {:className "btn btn-primary btn-lg" :type "submit" :value "Search"}))))

(defn render-table [app rows]
  (dom/div nil
           (dom/h2 nil (str "Found " (count rows) " results"))
           (om/build filtered-table app
             {:init-state {:rows rows
                           :table-attributes {:className "table table-striped"}}})))

(defn click-id-link [chan id e]
  (.log js/console e)
  (put! chan [:service.id id])
  false)

(defn search-results [app owner {:keys [chan]}]
  (om/component
   (dom/div
    #js {:id "search_results"}
    (if-let [result (:search-result app)]
      (do (.log js/console "DATA" result)
        (let [rows (->> (js->clj result :keywordize-keys true)
                        (mapv #(hash-map
                                :id (dom/a #js {:href "#" :onClick (partial click-id-link chan (:id %))}
                                           nil (:id %))
                                :name (:name %))))]
           (render-table app rows)))
      ""))))

;; consider reuse with search-results once this is more fleshed out
(defn id-results [app owner]
  (om/component
   (dom/div
    #js {:id "id_results"}
    (if-let [result (:id-result app)]
      (do (.log js/console "DATA" result)
        (->> (js->clj result :keywordize-keys true)
             (mapv (fn [[k v]]
                     {:id k :count (:count v) :values (pr-str (:values v))}))
             (render-table app)))
      ""))))

;; Event loop and main app
;; =======================

(defn handle-event [app event event-data {:keys [chan]}]
  (.log js/console "Event: " (pr-str event) event-data)
  (case event
    :service.search (fetch-search-results chan event-data)
    :service.id (fetch-id-results chan event-data)
    :ui.search  (om/update! app assoc :search-result (.-result event-data))
    :ui.id (om/update! app assoc
                       :search-result nil ;; temp hack to hide search table
                       :id-result (.-property event-data))

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
                             app
                             event
                             event-data
                             {:chan main-chan}))))))
      om/IRender
      (render [_]
              (dom/div nil
                       (dom/h1 nil "Welcome to Freebase Explorer!")
                       (dom/div #js {:className "jumbotron"}
                                nil
                                (om/build search-form app {:opts {:chan (om/get-state owner :chan)}}))
                       ;; Consider not rendering these when they have no results
                       (om/build search-results app {:opts {:chan (om/get-state owner :chan)}})
                       (om/build id-results app)))))

(om/root app-state om-freebase-explorer-app (.getElementById js/document "app"))
