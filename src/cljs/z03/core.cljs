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
   [goog.fx :as fx]
   [goog.fx.Dragger.EventType]
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

(def projects
  [{:id "ios7-templates"
    :description "iOS7 Template System"
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
     [:h4.clickable {:on-click #(reset! (:active-file ui-state) nil)} "Back"]]]])

(defonce file-annotations (r/atom []))

(defn click-on-annotation [_x _y]
  (let [r 13]
   (some (fn [{:keys [x y id] :as e}]
           (and (< _x (+ x r))
                (> _x (- x r))
                (< _y (+ y r))
                (> _y (- y r))
                e))
         @file-annotations)))

(def editable
  (let [image-scale (r/atom 1)
        image-x (r/atom 0)
        image-y (r/atom 0)
        space-down (r/atom false)
        key-down-listener #(when (= (.-keyCode %) goog.events.KeyCodes.SPACE) (reset! space-down true))
        key-up-listener #(when (= (.-keyCode %) goog.events.KeyCodes.SPACE) (reset! space-down false))
        annotation-edit (r/atom nil)]
    (with-meta
      (fn []
        [:div {:style {:position "absolute" :top 0 :left 0}}
         [:img {:style {:transform (gstring/format "scale(%s)" @image-scale)}
                :src "img/template_ios7.png"}]
         [:div {:style {:position "absolute" :top 0 :left 0 :width "100%" :height "100%"}}
          [:svg {:width "100%" :height "100%"
                 :style {:transform (gstring/format "scale(%s)" @image-scale)}}
           (for [{:keys [id x y]} @file-annotations]
             [:circle {:cx x :cy y
                       :r 10 :style {:fill "#f7a032" :stroke "rgba(255, 124, 43, 0.3)" :stroke-width "3px"}
                       :cursor "pointer"}])]
          (when-let [{:keys [x y]} @annotation-edit]
            [:div {:style {:position "absolute" :left x :top y}}
             [:textarea {:rows 4 :cols 50}]
             [:div
              [:h4.rfloat.link {:style {:margin-top 0} :on-click #(reset! annotation-edit nil)} "Save"]
              [:h4.rfloat.link {:style {:margin "0 10px 0 0"} :on-click #(reset! annotation-edit nil)} "Cancel"]]])]])
      {:component-did-mount
       (fn [this]
         (let [node (r/dom-node this)]
           (goog.events/listen js/document goog.events.EventType.KEYDOWN key-down-listener)
           (goog.events/listen js/document goog.events.EventType.KEYUP key-up-listener)
           (goog.events/listen (goog.events.MouseWheelHandler. node)
                               goog.events.MouseWheelHandler.EventType.MOUSEWHEEL
                               (fn [e] (swap! image-scale #(gmath/clamp (+ % (* 0.001 (.-deltaY e))) 1.0 4.0))))
           (goog.events/listen node
                               goog.events.EventType/MOUSEDOWN
                               (fn [e]
                                 (let [x (.-offsetX e) y (.-offsetY e)]
                                   (cond @space-down
                                         (let [drag (fx/Dragger. node)
                                               local-state (r/state this)]
                                           (.setLimits drag (goog.math.Rect. 0 0 400 400))
                                           #_(.addEventListener drag
                                                                goog.fx.Dragger.EventType/DRAG
                                                                (fn [d]
                                                                  (let [dx (-> d .-dragger .-deltaX)
                                                                        dy (-> d .-dragger .-deltaY)]
                                                                    (log* (str "x: " dx " y: " dy))
                                                                    (swap! image-x (fn [x] (gmath/clamp dx 0 x)))
                                                                    (swap! image-y (fn [y] (gmath/clamp dy 0 y))))))
                                           (.addEventListener drag goog.fx.Dragger.EventType/END #(.dispose drag))
                                           (.startDrag drag e))
                                         (not @annotation-edit)
                                         (if-let [ann (click-on-annotation x y)]
                                           (reset! annotation-edit {:x (+ 7 (:x ann)) :y (+ 7 (:y ann))})
                                           (swap! file-annotations conj {:x x :y y :id (rand-int 99999999)}))))))))
       :component-will-unmount
       (fn [this]
         (goog.events/unlisten js/document goog.events.EventType.KEYUP key-up-listener)
         (goog.events/unlisten js/document goog.events.EventType.KEYDOWN key-down-listener))})))

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
