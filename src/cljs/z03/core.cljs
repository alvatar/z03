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
   [goog.fx :as fx]
   [goog.fx.Dragger.EventType]
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

(def projects
  [{:id "google-animations"
    :description "Showcase Google animations"
    :commits ["[master] First version"]}
   {:id "rick-interstellar-enterprises-branding"
    :description "Rick Interstellar Enterprises: branding"
    :commits ["[master] Branding v1" "Simplified logo; reduced number of colors"]}
   {:id "adventure-time-branding"
    :description "Adventure Time: Branding"
    :commits ["[master] Final version" "Modifications from developer meeting" "Changed specifications for printing" "New layout for letter" "Complete change of palette"]}
   {:id "futurama-web-design"
    :description "Futurama: Web Design"
    :commits ["[master] Final version" "Mockup v4" "Mockup v3" "Mockup v2" "Mockup v1"]}
   {:id "portfolio-web"
    :description "Personal Portfolio: Web"
    :commits ["[master] Add Adventure Time" "Add Futurama Web Design" "I like this version" "Simplify and clean up design" "All the things in place"]}])

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
   :active-revision (r/atom (:master revisions))
   :active-file (r/atom nil)})

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
   [:h1 :h2 :h3 {:font-family ["Bitter" "sans-serif"]
                 :font-weight 700}]
   [:h1 {:line-height (u/rem 2.8)}]
   [:h2 {:line-height (u/rem 2.2)}]
   [:h3 {:line-height (u/rem 2.0)}]
   [:h4 {:line-height (u/rem 1.8)}]
   [:h4.link {:overflow-y "hidden"
              :cursor "pointer"
              :line-height (u/rem 1.5)
              :border {:style "solid" :width "0 0 1 0"}}]
   [:h5 {:line-height (u/rem 1.6)}]
   [:.fill-parent {:width "100%" :height "100%"}]
   [:.lfloat {:float "left"}]
   [:.rfloat {:float "right"}]
   [:.center {:text-align "center"}]
   [:.nomargin {:margin 0 :padding 0}]
   [:.clickable {:cursor "pointer"}]
   [:.divider {:border {:style "solid" :width "0 0 1 0"}}]
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
   [:.section-container {:position "absolute"
                         :top 0 :left 0
                         :height (u/px 280) :width "100%"}]
   [:.header-container {:position "absolute" :left 0 :top 0
                        :width "100%" :height (u/px 30)
                        :border {:style "solid" :width "0 0 1 0"}
                        :font {:family ["Bitter" "sans-serif"]
                               :weight 400
                               :size (u/rem 0.7)}}]
   [:.header-text
    [:h4 {:line-height (u/rem 0.8) :margin 0 :padding "0.5rem 0.5rem 0.5rem 0.5rem"}]]
   [:.project-list {:margin {:top (u/px 33) :bottom (u/rem 10)}}
    [:.item {:position "relative"
             :height (u/rem 10)
             :padding (u/rem 1)
             :border {:style "solid" :width "0 0 1 0"}}
     [:&:hover {:background {:color "#eee"}}]
     [:&:first-child {:border {:style "solid" :width "1 0 1 0"}}]]]
   [:.editor-container {:position "fixed"
                        :top 0 :left 0
                        :height "100%" :width "100%"
                        :background "url(img/editor-bg.png) top left repeat"
                        :background-attachment "fixed"
                        :background-position "top left"}]))

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
   ;; [:h2 {:style {:margin-top "5rem"}} "Projects"]
   [:div.project-list
    (for [{:keys [id description commits]} projects]
      [:div.item.clickable {:key id :on-click #(reset! (:active-project ui-state) id)}
       [:div {:style {:width "50%" :background-color "50%"}}
        [:h3 {:style {:margin-bottom "10px" :font-weight "bold"}} id]
        [:h4 {:style {:margin-top "10px"}} description]]
       [:div {:style {:position "absolute" :left "50%" :top 0 :margin-top "5rem"}}
        (for [c (take 5 commits)]
          [:h6 {:style {:color "#555" :margin "0 0 0 0"}} "- " c])]])]
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
       [:div {:style {:height "280px"}}
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

(defn file-ui-header []
  [:div.header-container
   [:div.header-text
    [:div.lfloat {:style {:margin-right "0.5rem"}}
     [:h4.clickable {:on-click #(reset! (:active-file ui-state) nil)} "Back"]]
    [:div.rfloat {:style {:margin-left "0.5rem"}}
     [:h4.clickable {:on-click #(js/alert "TODO")} "Add Annotation"]]]])

(def editable
  (let [image-scale (r/atom 1)
        image-x (r/atom 0)
        image-y (r/atom 0)]
    (with-meta
      (fn []
        [:div {:style {:position "absolute"}}
         [:img {:style {:transform (gstring/format "scale(%s)" @image-scale)}
                :src "img/google_motion_system.gif"}]])
      {:component-did-mount
       (fn [this]
         (let [node (r/dom-node this)]
           (goog.events/listen (goog.events.MouseWheelHandler. node)
                               goog.events.MouseWheelHandler.EventType.MOUSEWHEEL
                               (fn [e] (swap! image-scale #(+ % (* 0.001 (.-deltaY e))))))
           (goog.events/listen node
                               goog.events.EventType/MOUSEDOWN
                               (fn [e]
                                 (let [drag (fx/Dragger. node)
                                       local-state (r/state this)]
                                   (.setLimits drag (goog.math.Rect. 0 0 400 400))
                                   #_(.addEventListener drag
                                                      goog.fx.Dragger.EventType/DRAG
                                                      (fn [d])
                                                      #_(fn [d]
                                                        (let [dx (-> d .-dragger .-deltaX)
                                                              dy (-> d .-dragger .-deltaY)]
                                                          (log* (str "x: " dx " y: " dy))
                                                          (swap! image-x (fn [x] (gmath/clamp dx 0 x)))
                                                          (swap! image-y (fn [y] (gmath/clamp dy 0 y)))
                                                          )))
                                   (.addEventListener drag goog.fx.Dragger.EventType/END #(.dispose drag))
                                   (.startDrag drag e))))))})))

(defn file-viewer []
  [:div.editor-container
   [editable]])

(defn file-ui []
  [:div
   [file-viewer]
   [file-ui-header]])

(defn app []
  (let [canvas-dom (atom nil)
        monet-canvas (atom nil)]
    [:div.container
     (cond
       @(:active-file ui-state)
       [file-ui]
       @(:active-project ui-state)
       [project-ui]
       :else
       [home-ui])]))

;;
;; Init
;;

(r/render [app] (js/document.getElementById "app"))

(client/start-router!)
