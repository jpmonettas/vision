(ns vision.runner
    (:require [doo.runner :refer-macros [doo-tests]]
              [vision.core-test]))

(doo-tests 'vision.core-test)
