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
;; Actions
;;

(defn build-standard-request [id & [handler]]
  (fn [data & [cb]]
    (client/chsk-send!
     [id data] 30000
     (fn [resp]
       (if-not (and (sente/cb-success? resp) (= :ok (:status resp)))
         (do (log* resp)
             (js/alert (gstring/format "Ups... Error in %s ¯\\_(ツ)_/¯ Restart the app, please..." id)))
         (do (when handler (handler resp))
             (when cb (cb resp))))))))

(def get-user-initial-data
  (build-standard-request :user/get-initial-data
                          (fn [{:keys [projects]}]
                            (reset! (:projects ui-state) projects))))

(def get-project-initial-data
  (build-standard-request :project/get-initial-data
                          (fn [{:keys [commits]}]
                            (reset! (:commits ui-state) commits)
                            (reset! (:active-commit ui-state) (second (first commits))))))

;;
;; Event Handlers
;;

(defmethod client/-event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (let [[old-state-map new-state-map] (have vector? ?data)]
    (when (:first-open? new-state-map)
      nil)))

(defmethod client/-event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?user ?csrf-token ?handshake-data] ?data]
    ;; (reset! (:user-id app-state) ?user)
    ;; (when-not (= ?user :taoensso.sente/nil-uid)
    ;;   (log* "HANDSHAKE"))
    (get-user-initial-data)))

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
    (for [{:keys [name description latest-commits]} @(:projects ui-state)]
      [:div.item.clickable {:key name :on-click (fn []
                                                  (reset! (:active-project ui-state) name)
                                                  (get-project-initial-data {:name name}))}
       [:div {:style {:width "50%" :background-color "50%"}}
        [:h3 {:style {:margin-bottom "10px" :font-weight "bold"}} name]
        [:h4 {:style {:margin-top "10px"}} description]]
       [:div {:style {:position "absolute" :left "50%" :top 0 :margin-top "5rem"}}
        (for [[c idx] (map vector latest-commits (range))]
          [:h6 {:key (str name c) :style {:color "#555" :margin "0 0 0 0"}} "- " c])]])]
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
  (let [hover-commit (r/atom false)]
    (fn []
      (if-let [active-commit @(:active-commit ui-state)]
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
                       :on-click #(reset! (:active-commit ui-state) (get @(:commits ui-state) "master"))}]
             [:circle {:cx 646.5 :cy 186
                       :r 9 :style {:fill "#888"}
                       :cursor "pointer"
                       :on-mouse-over #(reset! hover-commit true)
                       :on-mouse-out #(reset! hover-commit false)
                       :on-click #(reset! (:active-commit ui-state) (get @(:commits ui-state) "head1"))}]
             (when @hover-commit
               [:g
                [:rect {:x 485 :y 193 :width 200 :height 20 :fill "#fff"}]
                [:text {:x 660 :y 208 :font-family "Oswald" :font-size "0.8rem" :text-anchor "end"} "Simplified logo; reduced number of colors"]])]]]]
         [:div
          [:h2 (:description active-commit)]
          [:div.grid
           [:div.col-12>h4.rfloat.link "get presentation link"]]
          [:div.files-listing
           (for [{:keys [file last-commit]} (:files active-commit)]
             [:div.grid {:key file :style {:height "40px" :border-style "solid" :border-width "0 0 1 0" :border-color "#ccc"}}
              [:div.file-item.col-3.clickable {:on-click #(reset! (:active-file ui-state) file)}
               [:p.nomargin {:style {:line-height "40px" :height "40px" :color "#008cb7"}} file]]
              [:div.file-item.col-9 [:p.nomargin {:style {:line-height "40px" :height "40px"}} last-commit]]])]]
         [project-ui-header]]
        [:div
         [:div.center-aligner
          [:div.spinner [:div.double-bounce1] [:div.double-bounce2]]]
         [project-ui-header]]))))

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
