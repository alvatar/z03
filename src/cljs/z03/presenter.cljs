(ns z03.presenter
  (:require
   [taoensso.encore :as encore :refer-macros (have have?)]
   [taoensso.timbre :as timbre :refer-macros (tracef debugf infof warnf errorf)]
   [cljs.core.async :as async :refer (<! >! put! take! chan)]
   [taoensso.sente :as sente :refer (cb-success?)]
   [taoensso.sente.packers.transit :as sente-transit]
   [reagent.core :as r]
   [datascript.core :as d]
   [posh.reagent :as p]
   [goog.style]
   [garden.core :refer [css]]
   [garden.units :as u]
   [garden.stylesheet :as stylesheet]
   [garden.color :as color :refer [hsl rgb]]
   [goog.string :as gstring]
   ;; -----
   [z03.style]
   [z03.globals :as globals :refer [db-conn display-type window]]
   [z03.utils :as utils :refer [log*]]
   [z03.client :as client])
  (:require-macros
   [garden.def :refer [defkeyframes]]))


(goog-define *is-dev* false)

(enable-console-print!)
(timbre/set-level! :debug)

;;
;; State
;;

(def revisions
  {:master {:description "[master] Branding v1"
            :tags ["branding" "logo" "milestone"]
            :files [{:file "logo.psd" :last-commit "[master] Branding v1"}
                    {:file "file1.psd" :last-commit "Simplified logo; reduced number of colors"}
                    {:file "file2.psd" :last-commit "[master] Branding v1"}
                    {:file "file3.png" :last-commit "[master] Branding v1"}
                    {:file "file4.jpg" :last-commit "[master] Branding v1"}
                    {:file "file5.png" :last-commit "[master] Branding v1"}
                    {:file "file6.jpg" :last-commit "[master] Branding v1"}]}
   :head1 {:description "Simplified logo; reduced number of colors"
           :tags ["logo" "tweak"]
           :files [{:file "logo.psd" :last-commit "Simplified logo; reduced number of colors"}
                   {:file "file1.psd" :last-commit "General structure for branding"}]}})

(defonce ui-state {})

;;
;; Event Handlers
;;

(defmethod client/-event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (let [[old-state-map new-state-map] (have vector? ?data)]
    (when (:first-open? new-state-map)
      ;; (log* new-state-map)
      nil
      )))

(defmethod client/-event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?user ?csrf-token ?handshake-data] ?data]
    ;; (reset! (:user-id app-state) ?user)
    (when-not (= ?user :taoensso.sente/nil-uid)
      nil
      ;;(log* "HANDSHAKE")
      )))

;;
;; UI Components
;;

(defn home-ui-header []
  [:div.header-container
   [:div.header-text
    [:h4.lfloat "iOS7 templates"]
    [:h4.rfloat "Design by Multiverse Visual Adventures Ltd."]]])

(defn home-ui []
  [:div.section-container
   [home-ui-header]])

(defn app []
  (let [canvas-dom (atom nil)
        monet-canvas (atom nil)]
    [:div.container
     [home-ui]]))

;;
;; Init
;;

(r/render [app] (js/document.getElementById "app"))

(client/start-router!)
