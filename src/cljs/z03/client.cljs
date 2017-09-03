(ns z03.client
  (:require
   [taoensso.sente :as sente]
   [taoensso.sente.packers.transit :as sente-transit]
   ;; -----
   [z03.globals :as globals]
   [z03.utils :as utils :refer [log*]]))


;;
;; Sente setup
;;

(let [packer (sente-transit/get-transit-packer)
      {:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket!
       "/chsk" {:host (let [conn (if (= globals/*env* "dev")
                                   (if globals/*enable-mobile-dev*
                                     (str globals/*server-ip* ":5000")
                                     "localhost:5000")
                                   "domain.com")]
                        (log* "Connecting to " conn))
                :protocol (if (= globals/*env* "dev") "http:" "https:")
                :type :auto
                :packer packer})]
  (def chsk chsk)
  (def ch-chsk ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def chsk-state state)) ; Watchable, read-only atom

;;
;; Sente event handlers
;;

(defmulti -event-msg-handler
  "Multimethod to handle Sente `event-msg`s"
  :id)

(defn event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler
  :default ; Default/fallback case (no other matching handler)
  [{:as ev-msg :keys [event]}]
  (log* "Unhandled event: " event))

(defmethod -event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (log* "Push event from server: " ?data))

(defonce router_ (atom nil))
(defn stop-router! [] (when-let [stop-f @router_] (stop-f)))
(defn start-router! []
  (stop-router!)
  (log* "Initializing Sente client router")
  (reset! router_
    (sente/start-client-chsk-router!
      ch-chsk event-msg-handler)))
