(ns vision.subs
    (:require-macros [reagent.ratom :refer [reaction]])
    (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 :current-frame
 (fn [db]
   (:current-frame db)))

(re-frame/reg-sub
 :stats
 (fn [db]
   (:stats db)))

(re-frame/reg-sub
 :obj
 (fn [db]
   (:obj db)))

(re-frame/reg-sub
 :threads-status
 (fn [db [_ thread]]
   (-> db :threads-status thread)))
