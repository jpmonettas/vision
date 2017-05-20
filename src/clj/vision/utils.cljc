(ns vision.utils)

(defn update-stats [stats k]
  (let [stats-every 20
        updated-stats (update-in stats [k :count] (fnil inc 0))]
    (if (zero? (mod (get-in updated-stats [k :count]) stats-every))
      (let [now #?(:clj (System/currentTimeMillis)
                   :cljs (.getTime (js/Date.)))]
        (-> updated-stats
            (assoc-in [k :fps] (float (/ 1000
                                         (/ 
                                          (- now (get-in stats [k :last] 0))
                                          stats-every)))) 
            (assoc-in [k :last] now)))
      updated-stats)))
