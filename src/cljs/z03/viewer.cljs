(ns z03.viewer
  (:require
   [taoensso.encore :as encore :refer-macros (have have?)]
   [taoensso.timbre :as timbre :refer-macros (tracef debugf infof warnf errorf)]
   [reagent.core :as r]
   [datascript.core :as d]
   [posh.reagent :as p]
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
   [z03.utils :as utils :refer [log*]]
   [z03.globals :as globals :refer [db-conn display-type window ui-state]])
  (:require-macros
   [garden.def :refer [defkeyframes]]))

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
