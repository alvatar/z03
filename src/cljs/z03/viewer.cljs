(ns z03.viewer
  (:require
   [taoensso.encore :as encore :refer-macros (have have?)]
   [taoensso.timbre :as timbre :refer-macros (tracef debugf infof warnf errorf)]
   [rum.core :as r :refer [defc defcs react]]
   [datascript.core :as d]
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
   [z03.globals :as globals :refer [db-conn display-type window app-state]])
  (:require-macros
   [garden.def :refer [defkeyframes]]))

(defc file-ui-header
  []
  [:div.header-container
   [:div.header-text
    [:div.lfloat {:style {:margin-right "0.5rem"}}
     [:h4.clickable {:on-click #(reset! (:active-file app-state) nil)} "Back"]]]])

(defonce file-annotations (atom []))

(defn click-on-annotation [_x _y]
  (let [r 13]
   (some (fn [{:keys [x y id] :as e}]
           (and (< _x (+ x r))
                (> _x (- x r))
                (< _y (+ y r))
                (> _y (- y r))
                e))
         @file-annotations)))

(defcs editable
  < r/reactive
  (r/local 1 ::image-scale)
  (r/local 0 ::image-x)
  (r/local 0 ::image-y)
  (r/local false ::space-down)
  (r/local nil ::key-down-listener)
  (r/local nil ::key-up-listener)
  (r/local nil ::annotation-edit)
  {:did-mount
   (fn [_state]
     (let [comp (:rum/react-component _state)
           node (js/ReactDOM.findDOMNode comp)]
       (reset! (::key-down-listener _state)
               (goog.events/listen js/document goog.events.EventType.KEYDOWN
                                   #(when (= (.-keyCode %) goog.events.KeyCodes.SPACE) (reset! (::space-down _state) true))))
       (reset! (::key-up-listener _state)
               (goog.events/listen js/document goog.events.EventType.KEYUP
                                   #(when (= (.-keyCode %) goog.events.KeyCodes.SPACE) (reset! (::space-down _state) false))))
       (goog.events/listen (goog.events.MouseWheelHandler. node)
                           goog.events.MouseWheelHandler.EventType.MOUSEWHEEL
                           (fn [e] (swap! (::image-scale _state) #(gmath/clamp (+ % (* 0.001 (.-deltaY e))) 1.0 4.0))))
       (goog.events/listen node
                           goog.events.EventType/MOUSEDOWN
                           (fn [e]
                             (let [x (.-offsetX e) y (.-offsetY e)]
                               (cond @(::space-down _state)
                                     (let [drag (fx/Dragger. node)]
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
                                     (not @(::annotation-edit _state))
                                     (if-let [ann (click-on-annotation x y)]
                                       (reset! (::annotation-edit _state) {:x (+ 7 (:x ann)) :y (+ 7 (:y ann))})
                                       (swap! file-annotations conj {:x x :y y :id (rand-int 99999999)})))))))
     _state)
   :will-unmount
   (fn [_state]
     (goog.events/unlistenByKey js/document goog.events.EventType.KEYUP @(::key-up-listener _state))
     (goog.events/unlistenByKey js/document goog.events.EventType.KEYDOWN @(::key-down-listener _state))
     _state)}
  [_state]
  (let [image-scale (::image-scale _state)
        image-x (::image-x _state)
        image-y (::image-y _state)
        space-down (::space-down _state)
        annotation-edit (atom nil)]
    [:div {:style {:position "absolute" :top 0 :left 0}}
     [:img {:style {:transform (gstring/format "scale(%s)" (react image-scale))}
            :src "/img/template_ios7.png"}]
     [:div {:style {:position "absolute" :top 0 :left 0 :width "100%" :height "100%"}}
      [:svg {:width "100%" :height "100%"
             :style {:transform (gstring/format "scale(%s)" (react image-scale))}}
       (for [{:keys [id x y]} @file-annotations]
         [:circle {:key (rand-int 999999999) ; TODO crap
                   :cx x :cy y
                   :r 10 :style {:fill "#f7a032" :stroke "rgba(255, 124, 43, 0.3)" :stroke-width "3px"}
                   :cursor "pointer"}])]
      (when-let [{:keys [x y]} (react (::annotation-edit _state))]
        [:div {:style {:position "absolute" :left x :top y}}
         [:textarea {:rows 4 :cols 50}]
         [:div
          [:h4.rfloat.link {:style {:margin-top 0} :on-click #(reset! (::annotation-edit _state) nil)} "Save"]
          [:h4.rfloat.link {:style {:margin "0 10px 0 0"} :on-click #(reset! (::annotation-edit _state) nil)} "Cancel"]]])]]))

(defc file-viewer
  []
  [:div.editor-container
   (editable)])

(defc file-ui
  < r/reactive
  []
  (log* (react (:active-file app-state)))
  [:div
   (file-viewer)
   (file-ui-header)])
