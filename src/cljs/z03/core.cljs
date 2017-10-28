(ns z03.core
  (:require
   [taoensso.encore :as encore :refer-macros (have have?)]
   [taoensso.timbre :as timbre :refer-macros (tracef debugf infof warnf errorf)]
   [cljs.core.async :as async :refer (<! >! put! take! chan)]
   [taoensso.sente :as sente :refer (cb-success?)]
   [taoensso.sente.packers.transit :as sente-transit]
   [reagent.core :as r]
   [datascript.core :as d]
   [posh.reagent :as p]
   [monet.canvas :as canvas]
   [goog.style]
   [garden.core :refer [css]]
   [garden.units :as u]
   [garden.stylesheet :as stylesheet]
   [garden.color :as color :refer [hsl rgb]]
   [goog.string :as gstring]
   [goog.math :as gmath]
   [goog.math.Rect]
   [goog.events]
   [goog.events.EventType]
   [goog.events.MouseWheelHandler]
   [goog.events.KeyCodes]
   [goog.events.KeyHandler]
   ;; [goog.fx :as fx]
   ;; [goog.fx.Dragger.EventType]
   ;; -----
   [z03.style]
   [z03.viewer :refer [file-ui]]
   [z03.globals :as globals :refer [db-conn display-type window ui-state]]
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

(defn home-ui-header []
  [:div.header-container
   [:div.header-text
    [:h4.lfloat.clickable {:on-click #(js/alert "User Settings (TODO)")} "Settings"]
    [:h4.rfloat.clickable
     @(p/q '[:find ?n .
             :where [?e]
             [?e :user/name ?n]]
           db-conn)]]])

(defn home-ui []
  [:div.section-container
   [:div.project-list
    (for [{:keys [id description commits]} globals/projects]
      [:div.item.clickable {:key id :on-click #(reset! (:active-project ui-state) id)}
       [:div {:style {:width "50%" :background-color "50%"}}
        [:h3 {:style {:margin-bottom "10px" :font-weight "bold"}} id]
        [:h4 {:style {:margin-top "10px"}} description]]
       [:div {:style {:position "absolute" :left "50%" :top 0 :margin-top "5rem"}}
        (for [c (take 5 commits)]
          [:h6 {:style {:color "#555" :margin "0 0 0 0"}} "- " c])]])]
   [home-ui-header]])

(defn logout [& [on-success-fn on-failure-fn]]
  (sente/ajax-lite "/logout"
                   {:method :post
                    :headers {:X-CSRF-Token (:csrf-token @client/chsk-state)}
                    :params {}}
                   (fn [ajax-resp]
                     (if (sente/cb-success? ajax-resp)
                       (when on-success-fn (on-success-fn))
                       (when on-failure-fn (on-failure-fn))))))

(defn project-ui-header []
  [:div.header-container
   [:div.header-text
    [:div.lfloat {:style {:margin-right "0.5rem"}}
     ;; [:i.fa.fa-undo {:aria-hidden "true"}]
     [:h4.clickable {:on-click #(reset! (:active-project ui-state) nil)} "Back"]]
    [:h4.lfloat.clickable {:on-click #(js/alert "Project Settings (TODO)")} "Settings"]
    [:h4.rfloat.clickable {:on-click (fn [] (logout #(utils/open-url "/" false)))} "Logout"]
    [:h4.rfloat
     @(p/q '[:find ?n .
             :where [?e]
             [?e :user/name ?n]]
           db-conn)]]])

(defn project-ui []
  (let [hover-revision (r/atom false)]
    (fn []
      [:div.section-container
       [:div {:style {:height "280px"}}
        [:div.center
         [:object {:type "image/svg+xml" :data "/svg/graph-prototype.svg"}
          "Your browser does not support SVG"]
         [:div {:style {:position "absolute" :top 0 :left 0}}
          [:svg {:width 750 :height 280}
           [:circle {:cx 699 :cy 186
                     :r 11 :style {:fill "#f7a032" :stroke "#666" :stroke-width "3"}
                     :cursor "pointer"
                     :on-click #(reset! (:active-revision ui-state) (:master globals/revisions))}]
           [:circle {:cx 646.5 :cy 186
                     :r 9 :style {:fill "#888"}
                     :cursor "pointer"
                     :on-mouse-over #(reset! hover-revision true)
                     :on-mouse-out #(reset! hover-revision false)
                     :on-click #(reset! (:active-revision ui-state) (:head1 globals/revisions))}]
           (when @hover-revision
             [:g
              [:rect {:x 485 :y 193 :width 200 :height 20 :fill "#fff"}]
              [:text {:x 660 :y 208 :font-family "Oswald" :font-size "0.8rem" :text-anchor "end"} "Simplified logo; reduced number of colors"]])]]]]
       (let [active-revision @(:active-revision ui-state)]
         [:div
          [:h2 (:description active-revision)]
          [:div.grid
           [:div.col-10>h5 "Tags: " (clojure.string/join ", " (:tags active-revision))]
           [:div.col-2>h4.rfloat.link "get presentation link"]]
          [:div.files-listing
           (for [{:keys [file last-commit]} (:files active-revision)]
             [:div.grid {:key file :style {:height "40px" :border-style "solid" :border-width "0 0 1 0" :border-color "#ccc"}}
              [:div.file-item.col-3.clickable {:on-click #(reset! (:active-file ui-state) file)}
               [:p.nomargin {:style {:line-height "40px" :height "40px" :color "#008cb7"}} file]]
              [:div.file-item.col-9 [:p.nomargin {:style {:line-height "40px" :height "40px"}} last-commit]]])]])
       [project-ui-header]])))

(defn app []
  [:div.container
   (cond
     @(:active-file ui-state)
     [file-ui]
     @(:active-project ui-state)
     [project-ui]
     :else
     [home-ui])])

;;
;; Init
;;

(r/render [app] (js/document.getElementById "app"))

(client/start-router!)
