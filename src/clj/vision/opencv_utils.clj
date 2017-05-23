(ns vision.opencv-utils
  (:require [base64-clj.core :as base64]
            [clojure.java.shell :refer [sh]])
  (:import [org.opencv.core Mat Core Scalar Size Point MatOfByte]
           [org.opencv.imgproc Imgproc]
           [org.opencv.highgui Highgui]))

(defn convert-color [^Mat m c]
  (let [result-m (Mat.)]
    (Imgproc/cvtColor m result-m (case c
                                   :bgr->hsv Imgproc/COLOR_BGR2HSV
                                   :bgr->gray Imgproc/COLOR_BGR2HSV
                                   :bgr->rgb Imgproc/COLOR_BGR2RGB))
    result-m))


(defn in-range-s [^Mat m [lch1 lch2 lch3] [hch1 hch2 hch3]]
  (let [result-m (Mat.)]
   (Core/inRange m
                 (Scalar. lch1 lch2 lch3)
                 (Scalar. hch1 hch2 hch3)
                 result-m)
   result-m))


(defn erode [^Mat m]
  (let [result-m (Mat.)]
    (Imgproc/erode m result-m (Imgproc/getStructuringElement Imgproc/MORPH_RECT (Size. 2 2)))
    result-m))

(defn dilate [^Mat m]
  (let [result-m (Mat.)]
    (Imgproc/dilate m
                    result-m
                    (Imgproc/getStructuringElement Imgproc/MORPH_RECT (Size. 2 2))
                    (Point. -1 -1)
                    5)
    result-m))

(defn bounding-boxes [^Mat m]
  (let [mclone (.clone m)
        l (java.util.ArrayList.)
        _ (Imgproc/findContours mclone l (Mat.) 0 1)]
    (sort-by :area >
             (map #(let [br (Imgproc/boundingRect %)
                         t-point (.tl br)
                         b-point (.br br)]
                     {:area (.area br)
                      :x1 (.-x t-point)
                      :y1 (.-y t-point)
                      :x2 (.-x b-point)
                      :y2 (.-y b-point)})
                  l))))


(defn encode-frame-b64 [frame-bytes]
  (String. (base64/encode-bytes frame-bytes)))

(defn mat->ext-byte-arr [^Mat m ext]
  (let [mob (MatOfByte.)]
    (Highgui/imencode (case ext
                        :png ".png"
                        :jpeg ".jpg")
                      m mob)
    (.toArray mob)))


(defn view-frame-matrix [^Mat fm]
  (let [tmp-img "/home/jmonetta/tmp/tmp-opencv.png"]
    (Highgui/imwrite tmp-img fm)
    (future (sh "feh" tmp-img))))

(defn pyr-down [^Mat m]
  (let [result-m (Mat.)]
    (Imgproc/pyrDown m result-m)
    result-m))
