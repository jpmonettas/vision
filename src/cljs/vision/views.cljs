(ns vision.views
    (:require [re-frame.core :as re-frame]
              [re-com.core :as re-com]))



(defn main-panel []
  (let [cf-sub (re-frame/subscribe [:current-frame])
        stats-sub (re-frame/subscribe [:stats])
        object-sub (re-frame/subscribe [:obj])]
    (fn []
      (let [stats @stats-sub]
       [:div
        [:img {:src (str "data:image/png;base64," @cf-sub)}]
        [:ul {:style {:display :inline-block}}
         (when-let [o @object-sub] [:li (str "Object at:" (str o))])
         [:li [:span "Browser received frames count: "] [:span (or (-> stats :frames-received :count ) 0)]]
         [:li [:span "Browser received fps: "] [:b (str (or (-> stats :frames-received :fps int) 0) " fps")]]
         [:li [:span "Server camera frames grabbed count: "] [:span (or (-> stats :frames-grabbed :count) 0)]]
         [:li [:span "Server camera frames grabbed fps: "] [:b (str (or (-> stats :frames-grabbed :fps int) 0) " fps")]]
         [:li [:span "Server frames processed count: "] [:span (or (-> stats :frames-processed :count) 0)]]
         [:li [:span "Server frames processed fps: "] [:b (str (or (-> stats :frames-processed :fps int) 0) " fps")]]
         [:li [:span "Server frames sent to browser count: "] [:span (or (-> stats :frames-sent-to-browser :count) 0)]]
         [:li [:span "Server frames sent to browser fps: "] [:b (str (or (-> stats :frames-sent-to-browser :fps int) 0) " fps")]]
         ]
        [:svg {:width 1000 :height 1000
               :style {:position :absolute
                       :top 0
                       :left 0}}
         (when-let [o @object-sub]
           [:rect {:x (:x1 o)
                   :y (:y1 o)
                   :width (- (:x2 o) (:x1 o))
                   :height (- (:y2 o) (:y1 o))
                   :style {:stroke :green
                           :fill-opacity 0.1}}])]
        ]))))
