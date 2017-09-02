(ns z03.core
  (:require
   [taoensso.encore :as encore :refer-macros (have have?)]
   [taoensso.timbre :as timbre :refer-macros (tracef debugf infof warnf errorf)]
   [cljs.core.async :as async :refer (<! >! put! take! chan)]
   [taoensso.sente :as sente :refer (cb-success?)]
   [taoensso.sente.packers.transit :as sente-transit]
   [cljs-react-material-ui.core :refer [get-mui-theme color]]
   [cljs-react-material-ui.icons :as ic]
   [cljs-react-material-ui.rum :as ui]
   [rum.core :as rum]))



(goog-define *is-dev* false)

(enable-console-print!)

;;
;; Utils
;;

(defn clj->json [ds] (.stringify js/JSON (clj->js ds)))

;;
;; Sente
;;

(defonce router_ (atom nil))

(declare event-msg-handler)

(defn init-sente! []
  (js/console.log "Initializing Sente...")
  (let [packer (sente-transit/get-transit-packer)
        {:keys [chsk ch-recv send-fn state]}
        (sente/make-channel-socket! "/chsk" { ;; :host (if *is-dev*
                                             ;;         (do (js/console.log "Connecting to localhost")
                                             ;;             "192.168.4.16:5000")
                                             ;;         (do (js/console.log "Connecting to Heroku")
                                             ;;             "ikidz.herokuapp.com"))
                                             ;; :protocol (if *is-dev* "http:" "https:")
                                             ;; :client-id id
                                             :type :auto
                                             :packer packer})]
    (def chsk chsk)
    (def ch-chsk ch-recv)             ; ChannelSocket's receive channel
    (def chsk-send! send-fn)          ; ChannelSocket's send API fn
    (def chsk-state state)            ; Watchable, read-only atom
    (defn stop-router! [] (when-let [stop-f @router_] (stop-f)))
    (defn start-router! []
      (stop-router!)
      (js/console.log "Initializing Sente client router")
      (reset! router_
              (sente/start-client-chsk-router!
               ch-chsk event-msg-handler)))
    (start-router!)))

(init-sente!)

;;
;; Event Handlers
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
  (js/console.log "Unhandled event: " event))

;; TODO: You'll want to listen on the receive channel for a [:chsk/state [_ {:first-open? true}]] event. That's the signal that the socket's been established.
(defmethod -event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (let [[old-state-map new-state-map] (have vector? ?data)]
    (if (:first-open? new-state-map)
      (let [username (:uid new-state-map)]
        (if (= username :taoensso.sente/nil-uid)
          (js/alert "Error logging in: cannot read user from Sente request")
          (js/console.log (str "Channel socket successfully established!: " new-state-map))))
      (js/console.log "Channel socket state change: " new-state-map))))

(defmethod -event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (js/console.log "Push event from server: " ?data))

(defmethod -event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (js/console.log "Handshake: " ?data)))

;;
;; UI Components
;;

(defn app []
  (ui/mui-theme-provider
   {:mui-theme (get-mui-theme {:palette {:text-color (color :blue600)}})}
   [:div {:style {:position "absolute"
                  :max-width "700px" :height "300px"
                  :margin "auto" :top "0" :bottom "0" :left "0" :right "0"}}
    (ui/paper
     [:div
      [:h2 {:style {:text-align "center"}} "Main"]
      [:h4 {:style {:text-align "center"}} "My app"]
      [:div {:style {:text-align "center"}}
       (ui/raised-button {:label "Login"
                          :style {:margin "1rem"}
                          :on-touch-tap
                          (fn [e] (chsk-send!
                                   [:user/store {:user-id 1}] 5000
                                   #(js/console.log "Received: " (clj->js %))))})]])]))

(rum/mount (app) (js/document.getElementById "app"))
