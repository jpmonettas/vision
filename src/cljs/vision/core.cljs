(ns vision.core
    (:require [reagent.core :as reagent]
              [re-frame.core :as re-frame]
              [re-frisk.core :refer [enable-re-frisk!]]
              [vision.events]
              [vision.subs]
              [vision.views :as views]
              [vision.config :as config]
              [cljs.core.async :as async :refer (<! >! put! chan)]
              [taoensso.sente  :as sente :refer (cb-success?)])
     (:require-macros
      [cljs.core.async.macros :as asyncm :refer (go go-loop)]))


(defn dev-setup []
  (when config/debug?
    (enable-console-print!)
    (enable-re-frisk! {:events? false})
    (println "dev mode")))

(defn mount-root []
  (re-frame/clear-subscription-cache!)
  (reagent/render [views/main-panel]
                  (.getElementById js/document "app")))

(defn ^:export init []
  (re-frame/dispatch-sync [:initialize-db])
  (dev-setup)
  (mount-root))




(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "ws://localhost:1234/chsk"
                                  {:type :ws
                                   :chsk-url-fn (constantly "ws://localhost:1234/chsk")})]
  (def ch-recv ch-recv))

(go-loop [msg nil]
  (when msg
    (let [[_ [ev-key frame-w-obj]] (:event msg)]
      (case ev-key
        :ev/new-frame (re-frame/dispatch [:new-frame-w-obj frame-w-obj])
        nil)))
  (recur (<! ch-recv)))

