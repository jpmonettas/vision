(ns vision.events
    (:require [re-frame.core :as re-frame :refer [debug]]
              [vision.db :as db]
              [vision.utils :as utils]))

(re-frame/reg-event-db
 :initialize-db
 (fn  [_ _]
   db/default-db))

(re-frame/reg-event-db
 :new-frame-w-obj
 (fn  [db [_ {:keys [frame obj]}]]
   (-> db
       (assoc :current-frame frame)
       (assoc :obj obj)
       (update :stats utils/update-stats :frames-received))))

(re-frame/reg-event-db
 :new-stats
 (fn  [db [_ stats]]
   (-> db 
       (update :stats merge stats))))

