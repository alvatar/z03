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
   ;; -----
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

(defonce ui-state
  {:active-project (r/atom "Example")
   :active-revision (r/atom (:master revisions))})

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

(defkeyframes drawer-animation-show
  [:from {:right (u/percent -50)}]
  [:to {:right 0}])

(defkeyframes drawer-animation-hide
  [:from {:right 0}]
  [:to {:right (u/percent -50)}])

(def styles
  ;; Ref http://www.webp.ch/fourre-tout/target/#!/dgellow.fourre_tout.garden
  (css
   {:vendors ["o" "moz" "webkit"]}
   ;; General
   (stylesheet/at-import "https://fonts.googleapis.com/css?family=Bitter:400,700|Oswald:200,500")
   [:body :h1 :h2 :h3 :h4 :h5 :h6 :p
    {:font-family ["Oswald" "sans-serif"]
     :font-weight 200
     :overflow-x "hidden"
     :line-height "1.2rem"}]
   [:h1 :h2 {:font-family ["Bitter" "sans-serif"]
             :font-weight 700}]
   [:h1 {:line-height (u/rem 2.8)}]
   [:h2 {:line-height (u/rem 2.2)}]
   [:.fill-parent {:width "100%" :height "100%"}]
   [:.lfloat {:float "left"}]
   [:.rfloat {:float "right"}]
   [:.center {:text-align "center"}]
   [:.nomargin {:margin 0 :padding 0}]
   [:.clickable {:cursor "pointer"}]
   ;; Colors
   [:.bg-aqua {:background-color "#7fdbff"}]
   ;; Animations
   drawer-animation-show
   drawer-animation-hide
   ;; Components
   (let [drawer {:position "absolute" :top 0
                 :width "50%" :height "100%"}]
     [[:.drawer-show (merge drawer {:right 0
                                    :animation [[drawer-animation-show "1.5s"]]})]
      [:.drawer-hide (merge drawer {:right (u/percent -50)
                                    :animation [[drawer-animation-hide "1.0s"]]})]])
   [:.file-menu]
   [:.file-item {:padding "0 2rem 1rem 2rem"}]
   [:.container {:position "relative"
                 :margin "0 auto"
                 :width (u/px 900)}]
   (let [files-top (u/px 280)]
     [[:.section-container {:position "absolute"
                            :top 0 :left 0
                            :height files-top :width "100%"
                            :background-color "#ddd"}]
      [:.header-container {:position "absolute" :left 0 :top 0
                           :width "100%" :height (u/px 30)
                           :border {:style "solid" :width "0 0 1 0"}
                           :font {:family ["Bitter" "sans-serif"]
                                  :weight 400
                                  :size (u/rem 0.7)}}]])
   [:.header-text
    [:h4 {:margin 0 :padding "0.5rem 0.5rem 0.5rem 0.5rem"}]]
   [:revision-description {:margin-left "2rem" :height "2.5rem"}]))

(defonce style-node (atom nil))
(if @style-node
  (goog.style/setStyles @style-node styles)
  (reset! style-node (goog.style/installStyles styles)))

(defn home-ui-header []
  [:div.header-container
   [:div.header-text
    [:h4.lfloat.clickable "Settings"]
    [:h4.rfloat.clickable
     @(p/q '[:find ?n .
             :where [?e]
             [?e :user/name ?n]]
           db-conn)]]])

(defn home-ui []
  [:div.section-container
   [:h1.clickable {:on-click #(reset! (:active-project ui-state) "Example")} "Hello"]
   [home-ui-header]])

(defn project-ui-header []
  [:div.header-container
   [:div.header-text
    [:div.lfloat {:style {:margin-right "0.5rem"}}
     ;[:i.fa.fa-undo {:aria-hidden "true"}]
     [:h4.clickable {:on-click #(reset! (:active-project ui-state) nil)} "Back"]]
    [:h4.lfloat.clickable "Settings"]
    [:h4.rfloat.clickable
     @(p/q '[:find ?n .
             :where [?e]
             [?e :user/name ?n]]
           db-conn)]]])

(defn project-ui []
  (let [hover-revision (r/atom false)]
    (fn []
      [:div.section-container
       [:div
        [:div.center
         [:object {:type "image/svg+xml" :data "svg/graph-prototype.svg"}
          "Your browser does not support SVG"]
         [:div {:style {:position "absolute" :top 0 :left 0}}
          [:svg {:width 750 :height 280}
           [:circle {:cx 699 :cy 186
                     :r 11 :style {:fill "#f7a032" :stroke "#666" :stroke-width "3"}
                     :cursor "pointer"
                     :on-click #(reset! (:active-revision ui-state) (:master revisions))}]
           [:circle {:cx 646.5 :cy 186
                     :r 9 :style {:fill "#888"}
                     :cursor "pointer"
                     :on-mouse-over #(reset! hover-revision true)
                     :on-mouse-out #(reset! hover-revision false)
                     :on-click #(reset! (:active-revision ui-state) (:head1 revisions))}]
           (when @hover-revision
             [:g
              [:rect {:x 485 :y 193 :width 200 :height 20 :fill "#fff"}]
              [:text {:x 660 :y 208 :font-family "Oswald" :font-size "0.8rem" :text-anchor "end"} "Simplified logo; reduced number of colors"]])]]]]
       (let [active-revision @(:active-revision ui-state)]
         [:div
          [:h2.revision-description (:description active-revision)]
          [:h5.revision-description "Tags: " (clojure.string/join ", " (:tags active-revision))]
          [:div.files-listing
           (for [{:keys [file last-commit]} (:files active-revision)]
             [:div.grid {:key file :style {:height "40px" :border-style "solid" :border-width "0 0 1 0" :border-color "#ccc"}}
              [:div.file-item.col-3 [:p.nomargin {:style {:line-height "40px" :height "40px" :color "#008cb7" :cursor "pointer"}} file]]
              [:div.file-item.col-9 [:p.nomargin {:style {:line-height "40px" :height "40px"}} last-commit]]])]])
       [project-ui-header]])))

(defn app []
  (let [canvas-dom (atom nil)
        monet-canvas (atom nil)]
    [:div.container
     (if @(:active-project ui-state)
       [project-ui]
       [home-ui])]))

;;
;; Init
;;

(r/render [app] (js/document.getElementById "app"))

(client/start-router!)
