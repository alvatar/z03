(ns z03.test-runner
  (:require
   [doo.runner :refer-macros [doo-tests]]
   [z03.core-test]
   [z03.common-test]))

(enable-console-print!)

(doo-tests 'z03.core-test
           'z03.common-test)
