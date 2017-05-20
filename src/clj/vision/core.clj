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
                            cv-utils/bgr->hsv
                            (cv-utils/in-range-s [10 120 100] [20 200 200])
                            cv-utils/erode
                            cv-utils/dilate
                            add-biggest-object-box)]
    (send stats-agent update-stats :frames-processed)
    processed-frame))

(defn build-frame-for-ws [{:keys [frame-matrix biggest-object]}]
  {:frame (-> frame-matrix
              cv-utils/mat->byte-arr
              cv-utils/encode-frame-b64)
   :obj biggest-object
   :server-stats (-> @stats-agent
                     (update :frames-grabbed dissoc :last)
                     (update :frames-processed dissoc :last)
                     (update :frames-sent-to-browser dissoc :last))})

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
(.start (Thread. (fn []
                   (loop [frame nil]
                     (when @ws-pusher-thread-running
                       (when frame
                         (send-thru-ws :sente/all-users-without-uid [:ev/new-frame frame])
                         (send stats-agent update-stats :frames-sent-to-browser)
                         (Thread/sleep 100)))
                     (recur (a/<!! ws-ch))))))

(def frame-retrieve-thread-running (atom false))
(.start (Thread. (fn []
                   (loop []
                     (when @frame-retrieve-thread-running
                       (let [frame (Mat.)]
                         (.read vc frame)
                         (a/>!! frames-ch frame)
                         (send stats-agent update-stats :frames-grabbed)
                         (Thread/sleep 10)))
                     (recur)))))


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


  (cv-utils/view-frame-matrix frame-mat)

  )
