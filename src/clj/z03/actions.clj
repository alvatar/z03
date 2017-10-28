(ns z03.actions
  (:gen-class)
  (:require [clojure.pprint :refer [pprint]]
            ;; Environment and configuration
            [environ.core :refer [env]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.aleph :refer (get-sch-adapter)]
            [taoensso.sente.packers.transit :as sente-transit]
            ;; -----
            [z03.database :as db]))

;;
;; Sente event handlers
;;

(defmulti -event-msg-handler
  "Multimethod to handle Sente `event-msg`s"
  ;; Dispatch on event-id
  :id)

(defn event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg) ; Handle event-msgs on a single thread
  ;; (future (-event-msg-handler ev-msg)) ; Handle event-msgs on a thread pool
  )

(defmethod -event-msg-handler :default ; Default/fallback case (no other matching handler)
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid (:uid session)]
    (when ?reply-fn
      (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))

(defmethod -event-msg-handler :user/get-initial-data
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (if-let [projects (db/project-find-by :user-id 1)];;TODO: from session
    (?reply-fn {:status :ok :projects projects})
    (?reply-fn {:status :error})))

;;
;; Sente event router (`event-msg-handler` loop)
;;

(defonce router_ (atom nil))

(defn stop-sente-router! [] (when-let [stop-fn @router_] (stop-fn)))

(defn start-sente-router! [ch-chsk]
  (stop-sente-router!)
  (reset! router_
          (sente/start-server-chsk-router!
           ch-chsk
           event-msg-handler)))

