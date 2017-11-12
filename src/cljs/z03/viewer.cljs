(ns z03.viewer
  (:require
   [oops.core :refer [oget oset!]]
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
   [z03.utils :as utils :refer [log* log**]]
   [z03.globals :as globals :refer [display-type window app-state]])
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

(defcs image-viewer
  < r/reactive
  (r/local 1 ::image-scale)
  (r/local 0 ::image-x)
  (r/local 0 ::image-y)
  (r/local false ::space-down)
  (r/local nil ::key-down-listener)
  (r/local nil ::key-up-listener)
  (r/local nil ::mouse-wheel-handler)
  (r/local nil ::mouse-down-handler)
  {:did-mount
   (fn [_state]
     (let [comp (:rum/react-component _state)
           node (js/ReactDOM.findDOMNode comp)
           object1 (js/document.getElementById "object1")]
       (oset! object1
              "onload"
              (fn []
                (reset! (::image-x _state) (/ (- (oget js/window "innerWidth")
                                                 (oget object1 "width"))
                                              2))
                (reset! (::image-y _state) (gmath/clamp (/ (- (oget js/window "innerHeight")
                                                              (oget object1 "height"))
                                                           2)
                                                        0
                                                        js/Number.MAX_SAFE_INTEGER))))
       (reset! (::key-down-listener _state)
               (goog.events/listen js/document goog.events.EventType.KEYDOWN
                                   #(when (= (.-keyCode %) goog.events.KeyCodes.SPACE) (reset! (::space-down _state) true))))
       (reset! (::key-up-listener _state)
               (goog.events/listen js/document goog.events.EventType.KEYUP
                                   #(when (= (.-keyCode %) goog.events.KeyCodes.SPACE) (reset! (::space-down _state) false))))
       (reset! (::mouse-wheel-handler _state)
               (goog.events/listen (goog.events.MouseWheelHandler. node)
                                   goog.events.MouseWheelHandler.EventType.MOUSEWHEEL
                                   (fn [e] (swap! (::image-scale _state) #(gmath/clamp (+ % (* 0.001 (.-deltaY e))) 1.0 4.0)))))
       (reset! (::mouse-down-handler _state)
               (goog.events/listen node
                                   goog.events.EventType/MOUSEDOWN
                                   (fn [e]
                                     (let [x (.-offsetX e) y (.-offsetY e)]
                                       (cond @(::space-down _state)
                                             (let [drag (fx/Dragger. node)]
                                               (.setLimits drag (goog.math.Rect. 0 0 400 400))
                                               (.addEventListener drag
                                                                  goog.fx.Dragger.EventType/DRAG
                                                                  (fn [d]
                                                                    (let [x (-> d .-dragger .-deltaX)
                                                                          y (-> d .-dragger .-deltaY)]
                                                                      (reset! (::image-x _state) x)
                                                                      (reset! (::image-y _state) y))))
                                               (.addEventListener drag goog.fx.Dragger.EventType/END #(.dispose drag))
                                               (.startDrag drag e))))))))
     _state)
   :will-unmount
   (fn [_state]
     (goog.events/unlistenByKey @(::key-up-listener _state))
     (goog.events/unlistenByKey @(::key-down-listener _state))
     (goog.events/unlistenByKey @(::mouse-wheel-handler _state))
     (goog.events/unlistenByKey @(::mouse-down-handler _state))
     _state)}
  [_state]
  (let [image-scale (::image-scale _state)
        image-x (::image-x _state)
        image-y (::image-y _state)
        space-down (::space-down _state)
        ;; TODO: user, project, commit
        file-url (gstring/format "/u/%s/%s/blob/%s/%s" "thor" @(:active-project app-state) "master" @(:active-file app-state))]
    [:img#object1 {:style {:max-width "100%" :max-height "100%"
                           :transform (gstring/format "translate(%spx, %spx) scale(%s)"
                                                      (react image-x)
                                                      (react image-y)
                                                      (react image-scale))}
                   :src file-url}]))

(defc file-viewer
  []
  [:div.editor-container
   [:div.limit
    (image-viewer)]])

(defc file-ui
  < r/reactive
  []
  [:div
   (file-viewer)
   (file-ui-header)])
