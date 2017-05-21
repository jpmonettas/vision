(ns vision.views
    (:require [re-frame.core :as re-frame]
              [re-com.core :as re-com]))

(defn listen [s]
  @(re-frame/subscribe s))

(defn camera [x y text]
  [:g
   [:rect {:x x :y y :width 50 :height 50 :fill :green}]
   [:text {:x x :y (+ y 15) } text]])

(defn browser [count fps x y]
  [:g
   [:rect {:x x :y y :width 90 :height 50 :fill :green}]
   [:text {:x x :y (+ y 15) } "Browser"]
   [:text {:x x :y (+ y 30) } (str (or count 0)
                                   " @ "
                                   (int (or fps 0)) " fps")]])

(defn switchable-thread [x y text on? on-click]
  [:g {:on-click on-click :style {:cursor (if (not (nil? on?)) :pointer :default)}}
   [:rect {:x x :y y :width 50 :height 50 :fill :green}]
   [:text {:x x :y (+ y 15) } text]
   (when (not (nil? on?)) [:text {:x x :y (+ y 30) } (if on? "[ON]" "[OFF]")])])

(defn frame-chan [count fps x1 y1 x2 y2]
  [:g
   [:line {:x1 x1 :y1 y1 :x2 x2 :y2 y2 :stroke :green}]
   [:text {:x (+ x1 (/ (- x2 x1) 2) -50) :y (+ y1 (/ (- y2 y1) 2)) :style {:fill :green}}
    (str (or count 0)
         " @ "
         (int (or fps 0)) " fps")]])

(defn stats-panel []
  [:svg {:width 1000
         :height 250
         :style {:display :flex
                 :position :relative
                 :top 60}}
   [:rect {:x 0 :y 0 :width 1000 :height 170 :style {:fill :black :stroke :green :stroke-width 3}}]
   [switchable-thread 100 15 "C1 read"
    (listen [:threads-status :c1-grab-frames-thread])
    #(re-frame/dispatch [:toggle-thread :c1-grab-frames-thread])]
   
   [switchable-thread 100 100 "C2 read"
    (listen [:threads-status :c2-grab-frames-thread])
    nil]

   [frame-chan
    (-> (listen [:stats]) :frames-grabbed :count)
    (-> (listen [:stats]) :frames-grabbed :fps)
    150 45 400 75]
   [frame-chan
    nil nil
    150 125 400 75]
   [switchable-thread 400 55 "Process" nil nil]
   
   [frame-chan
    (-> (listen [:stats]) :frames-processed :count)
    (-> (listen [:stats]) :frames-processed :fps)
    450 75 600 75]
   [switchable-thread 600 55 "To Bro"
    (listen [:threads-status :send-to-browser-thread])
    #(re-frame/dispatch [:toggle-thread :send-to-browser-thread])]
   

   [frame-chan
    (-> (listen [:stats]) :frames-sent-to-browser :count)
    (-> (listen [:stats]) :frames-sent-to-browser :fps)
    650 75 800 75]
   [browser
    (-> (listen [:stats]) :frames-received :count)
    (-> (listen [:stats]) :frames-received :fps)
    800 55]])

(defn main-panel []
  [:div {:style {:display :flex
                 :flex-direction :column
                 :align-items :center}}
   (when-let [frame-data (listen [:current-frame])]
     [:div {:style {:position :relative :display :flex :width 320 :height 240 :top 10}}
      [:img {:src (str "data:image/png;base64," frame-data)
             :style {:position :absolute
                     :top 0
                     :border "3px green solid"
                     :left 0}}]

      [:svg {:width "100%" :height "100%"
             :style {:position :absolute
                     :top 0
                     :left 0}} 
       (when-let [o (listen [:obj])]
         [:g
          [:rect {:x (:x1 o)
                  :y (:y1 o)
                  :width (- (:x2 o) (:x1 o))
                  :height (- (:y2 o) (:y1 o))
                  :style {:stroke :green
                          :fill-opacity 0.1}}]
          [:text {:x (:x1 o) :y (:y1 o) :fill :green} (str "(" (:x1 o) "," (:y1 o) ")")]])]])
   [stats-panel]])
