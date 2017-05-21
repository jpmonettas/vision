(ns vision.core
  (:require [clojure.core.async :as a]
            [taoensso.sente :as sente]
            [org.httpkit.server :as http-server]
            [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [vision.utils :refer [update-stats]]
            [clojure.string :as str]
            [vision.opencv-utils :as cv-utils])
  (:import [org.opencv.core Point Rect Size MatOfByte Mat]
           [org.opencv.highgui VideoCapture Highgui]))

(clojure.lang.RT/loadLibrary org.opencv.core.Core/NATIVE_LIBRARY_NAME)

(def stats-agent (agent {}))

(def vc (VideoCapture. 0))

;;;;;;;;;;;;;;;;;;;;;;;
;; Frames Processing ;;
;;;;;;;;;;;;;;;;;;;;;;;

(defn add-biggest-object-box [^Mat bin-m]
  {:frame-matrix bin-m
   :biggest-object (-> bin-m
                       (cv-utils/bounding-boxes)
                       first)})

(defn process-frame [^Mat m]
  (let [processed-frame (-> m
                            cv-utils/pyr-down
                            cv-utils/bgr->hsv
                            (cv-utils/in-range-s [10 120 100] [20 200 200])
                            cv-utils/erode
                            cv-utils/dilate
                            add-biggest-object-box)]
    (send stats-agent update :frames-processed (fnil inc 0))
    processed-frame))

(defn build-frame-for-ws [{:keys [frame-matrix biggest-object]}]
  {:frame (-> frame-matrix
              cv-utils/mat->byte-arr
              cv-utils/encode-frame-b64)
   :obj biggest-object})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Channels and pipelines ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ws-ch (a/chan (a/sliding-buffer 1)))
(def frames-ch (a/chan (a/sliding-buffer 1)))
(a/pipeline 5
            ws-ch
            (comp
             (map process-frame)
             (map build-frame-for-ws))
            frames-ch)

(def chsk (sente/make-channel-socket! (get-sch-adapter) {}))
(defn send-thru-ws [id ev]
  ((:send-fn chsk) id ev))

;;;;;;;;;;;;;;
;; Threads  ;;
;;;;;;;;;;;;;;

(def ws-pusher-thread-running (atom false))
(a/go-loop [frame nil]
  (when @ws-pusher-thread-running
    (when frame
      (send-thru-ws :sente/all-users-without-uid [:ev/new-frame frame])
      (send stats-agent update :frames-sent-to-browser (fnil inc 0))
      (a/<! (a/timeout 100))))
  (recur (a/<! ws-ch)))

(def frame-retrieve-thread-running (atom false))
(a/go-loop []
  (when (and @frame-retrieve-thread-running
             (.isOpened vc))
    (let [frame (Mat.)]
      (when (.read vc frame)
        (a/>! frames-ch frame)
        (send stats-agent update :frames-grabbed (fnil inc 0)))))
  (a/<! (a/timeout 10))
  (recur))


(defn calculate-stats [delta-millis last-counts current-counts]
  (reduce
   (fn [r [id counter]]
     (-> r
         (assoc id {:count counter
                    :fps (let [delta-count (- counter (get last-counts id 0))]
                           (if (zero? delta-count)
                             0
                             (float (/ 1000
                                       (/ delta-millis delta-count)))))})))
   {}
   current-counts))

;; Send stats every .5 seconds
(a/go-loop [last-counts @stats-agent]
  (let [check-every 500]
    (a/<! (a/timeout check-every))
    (let [current-counts @stats-agent]
      (send-thru-ws :sente/all-users-without-uid
                    [:ev/new-stats {:frames-stats (calculate-stats check-every last-counts current-counts)
                                  :threads-status {:c1-grab-frames-thread @frame-retrieve-thread-running
                                                   :c2-grab-frames-thread false
                                                   :send-to-browser-thread @ws-pusher-thread-running}}])
     (recur current-counts))))

(defn dispatch-incomming-ws-message [[ev-id data]]
  (case ev-id
    :ev/toggle-thread (case data
                        :c1-grab-frames-thread (swap! frame-retrieve-thread-running not)
                        :send-to-browser-thread (swap! ws-pusher-thread-running not))
    nil))

;; incomming websocket messages read thread
(a/go-loop []
  (dispatch-incomming-ws-message (:event (a/<! (:ch-recv chsk))))
    (recur))

;;;;;;;;;;;;;;;;
;; Web server ;;
;;;;;;;;;;;;;;;;

(def o *out*)

(Thread/setDefaultUncaughtExceptionHandler
   (reify
     Thread$UncaughtExceptionHandler
     (uncaughtException [this thread throwable]
       (binding [*out* o]
        (println  (format "Uncaught exception %s on thread %s" throwable thread))))))

(defonce server (http-server/run-server
               (-> (fn [req]
                     (if (= (:uri req) "/chsk")
                       (case (:request-method req)
                         :get ((:ajax-get-or-ws-handshake-fn chsk) req)
                         :post (:ajax-post-fn req))
                       {:status 404}))
                   wrap-keyword-params
                   wrap-params)

               {:port 1234}))

;;;;;;;;;;;;;;;;;;
;; For the repl ;;
;;;;;;;;;;;;;;;;;;

(comment
  
  ;; Run threads
  (reset! frame-retrieve-thread-running true)
  (reset! ws-pusher-thread-running true)

  ;; Stop threads
  (reset! frame-retrieve-thread-running false)
  (reset! ws-pusher-thread-running false)
  
  ;; stop the server
  (server)


  (.release vc)
  (.open vc "http://192.168.1.107:8080/video")
  
  (def frame-mat (Mat.))
  (.read vc frame-mat)
  (send-thru-ws :sente/all-users-without-uid [:ev/new-frame (-> {:frame-matrix frame-mat}
                                                                build-frame-for-ws)] )
  (send-thru-ws :sente/all-users-without-uid [:ev/new-frame (-> frame-mat
                                                                process-frame
                                                                build-frame-for-ws)] )


  (cv-utils/view-frame-matrix (cv-utils/pyr-down frame-mat))

  )
