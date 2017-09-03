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
   [garden.core :refer [css]]
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

(def styles
  (str "@import url('https://fonts.googleapis.com/css?family=Oswald:400,700');\n"
       (css [:body {:font-family "'Oswald', sans-serif"}]
            [:.bg-aqua {:background-color "#7fdbff"}]
            [:.file-menu {}]
            [:.file-item {:padding "2rem 2rem 2rem 2rem"}])))

(defonce style-node
  (let [node (js/document.createElement "style")]
    (js/document.head.appendChild node)
    node))

(aset style-node "innerHTML" styles)

(defn app []
  #_(str "HELLO "
         @(p/q '[:find ?n .
                 :where [?e]
                 [?e :user/name ?n]]
               db-conn)
         "!")
  [:div.container
   [:div.drawer.bg-aqua {:style {:position "absolute"
                                 :right 0 :top 0
                                 :width "50%" :height "100%"}}
    [:h2 {:style {:margin-left "2rem"}} "Files in checkpoint"]
    [:div.file-menu
     (let [files ["file1.psd" "file2.psd" "file3.png" "file4.jpg" "file5.png" "file6.jpg"]]
       (for [f files]
         [:div.file-item.col f]))]]])

;;
;; Init
;;

(r/render [app] (js/document.getElementById "app"))

(client/start-router!)
