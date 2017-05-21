(ns vision.fxs
  (:require [re-frame.core :as re-frame]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            [taoensso.sente  :as sente :refer (cb-success?)])
  (:require-macros
      [cljs.core.async.macros :as asyncm :refer (go go-loop)]))

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "ws://localhost:1234/chsk"
                                  {:type :ws
                                   :chsk-url-fn (constantly "ws://localhost:1234/chsk")})]
  (def ch-recv ch-recv)
  (def send-fn send-fn))

(go-loop [msg nil]
  (when msg
    (let [[_ [ev-key data]] (:event msg)]
      (case ev-key
        :ev/new-frame (re-frame/dispatch [:new-frame-w-obj data])
        :ev/new-stats (re-frame/dispatch [:new-stats data])
        nil)))
  (recur (<! ch-recv)))

(re-frame/reg-fx
 :ws-send
 (fn [ev]
   (.log js/console "Sending " ev)
   (send-fn ev)))



