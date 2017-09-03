(ns z03.core
  (:require
   [taoensso.encore :as encore :refer-macros (have have?)]
   [taoensso.timbre :as timbre :refer-macros (tracef debugf infof warnf errorf)]
   [cljs.core.async :as async :refer (<! >! put! take! chan)]
   [taoensso.sente :as sente :refer (cb-success?)]
   [taoensso.sente.packers.transit :as sente-transit]
   [reagent.core :as r]
   [posh.reagent :as p]
   [datascript.core :as d]
   ;; -----
   [z03.utils :as utils :refer [log*]]
   [z03.client :as client]))


(goog-define *is-dev* false)

(enable-console-print!)
(timbre/set-level! :debug)

;;
;; UI globals
;;

(def db-schema {})
(def db-conn (d/create-conn db-schema))
(p/posh! db-conn)

(d/transact! db-conn
             [{:user/name "Alvatar"}])

;;
;; Utils
;;

(defn clj->json [ds] (.stringify js/JSON (clj->js ds)))

;;
;; Sente
;;

(defonce router_ (atom nil))

(declare event-msg-handler)

;;
;; Event Handlers
;;

(defmethod client/-event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (let [[old-state-map new-state-map] (have vector? ?data)]
    (when (:first-open? new-state-map)
      (log* new-state-map))))

(defmethod client/-event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?user ?csrf-token ?handshake-data] ?data]
    ;; (reset! (:user-id app-state) ?user)
    (when-not (= ?user :taoensso.sente/nil-uid)
      (log* "HANDSHAKE"))))

;;
;; UI Components
;;

(defn app []
  [:p (str "HELLO "
           @(p/q '[:find ?n .
                   :where [?e]
                   [?e :user/name ?n]]
                 db-conn)
           "!")])

(r/render [app] (js/document.getElementById "app"))

(client/start-router!)
