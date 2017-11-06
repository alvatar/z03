(ns z03.presenter
  (:require
   [taoensso.encore :as encore :refer-macros (have have?)]
   [taoensso.timbre :as timbre :refer-macros (tracef debugf infof warnf errorf)]
   [cljs.core.async :as async :refer (<! >! put! take! chan)]
   [taoensso.sente :as sente :refer (cb-success?)]
   [taoensso.sente.packers.transit :as sente-transit]
   [rum.core :as r]
   [datascript.core :as d]
   [goog.style]
   [garden.core :refer [css]]
   [garden.units :as u]
   [garden.stylesheet :as stylesheet]
   [garden.color :as color :refer [hsl rgb]]
   [goog.string :as gstring]
   ;; -----
   [z03.style]
   [z03.viewer :refer [file-ui]]
   [z03.globals :as globals :refer [db-conn display-type window app-state]]
   [z03.utils :as utils :refer [log*]]
   [z03.client :as client])
  (:require-macros
   [garden.def :refer [defkeyframes]]))


(goog-define *is-dev* false)

(enable-console-print!)
(timbre/set-level! :debug)

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

(defn ui-header []
  [:div.header-container
   [:div.header-text
    [:h4.lfloat "iOS7 templates"]
    [:h4.rfloat "Design by Multiverse Visual Adventures Ltd."]]])

(def files [{:file "file1.psd"
             :thumbnail "img/template_ios7.png"
             :offset-x 0
             :offset-y 0}
            {:file "file2.psd"
             :thumbnail "img/template_ios7.png"
             :offset-x 50
             :offset-y 500}
            {:file "file3.psd"
             :thumbnail "img/template_ios7.png"
             :offset-x 700
             :offset-y 400}
            {:file "file4.psd"
             :thumbnail "img/template_ios7.png"
             :offset-x 200
             :offset-y 400}])

(defn presenter-ui []
  [:div.presenter-container
   [:div.file-list.grid-3
    (for [{:keys [file thumbnail offset-x offset-y]} (cons {:h "s"} files)]
      [:div.file-item-container.col {:key file}
       [:div.file-item.clickable
        (if file
          [:div.file-thumbnail {:on-click #(reset! (:active-file app-state) "whatever")
                                :style {:background-image (gstring/format "url(\"%s\")" thumbnail)
                                        :background-position (gstring/format "%spx %spx" offset-x offset-y)}}
           [:h2 {:style {:margin-left "20px"}} file]]
          [:div
           [:h3 "Project Report"]
           [:h4 {:style {:margin 0}} "Description"]
           [:h6 {:style {:margin 0}} "Latest modifications as disucssed in meeting [...]"]
           [:h4 {:style {:margin-top "1rem" :margin-bottom 0}} "Changelog"]
           [:h6 {:style {:margin 0}} "- Reduce number of colors"]
           [:h6 {:style {:margin 0}} "- Simplify logo"]])]])]
   [ui-header]])

(defn app []
  (let [canvas-dom (atom nil)
        monet-canvas (atom nil)]
    [:div.container
     (cond
       @(:active-file app-state)
       [file-ui]
       :else
       [presenter-ui])]))

;;
;; Init
;;

(r/mount app (js/document.getElementById "app"))

(client/start-router!)
