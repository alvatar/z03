(ns z03.core
  (:require
   [taoensso.encore :as encore :refer-macros (have have?)]
   [taoensso.timbre :as timbre :refer-macros (tracef debugf infof warnf errorf)]
   [cljs.core.async :as async :refer (<! >! put! take! chan)]
   [taoensso.sente :as sente :refer (cb-success?)]
   [taoensso.sente.packers.transit :as sente-transit]
   [reagent.core :as r]
   [posh.reagent :as p]
   [monet.canvas :as canvas]
   [goog.style]
   [garden.core :refer [css]]
   [garden.core :as u]
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

(defkeyframes drawer-animation
  [:from {:right "-50%"}]
  [:to {:right 0}])

(def styles
  ;; Ref http://www.webp.ch/fourre-tout/target/#!/dgellow.fourre_tout.garden
  (css
   {:vendors ["o" "moz" "webkit"]}
   ;; General
   (stylesheet/at-import "https://fonts.googleapis.com/css?family=Bitter:400,700|Oswald:200,500")
   [:body :h1 :h2 :h3 :h4 :h5 :h6 :p
    {:font-family ["Oswald" "sans-serif"]
     :font-weight 200
     :overflow-x "hidden"}]
   [:h1 :h2 {:font-family ["Bitter" "sans-serif"]
             :font-weight 700}]
   [:.fill-parent {:width "100%" :height "100%"}]
   ;; Colors
   [:.bg-aqua {:background-color "#7fdbff"}]
   ;; Animations
   drawer-animation
   ;; Components
   [:.drawer {:position "absolute"
              :right 0 :top 0
              :width "50%" :height "100%"
              :animation [[drawer-animation "1.5s"]]}]
   [:.file-menu]
   [:.file-item {:padding "2rem 2rem 2rem 2rem"}]))

(defonce style-node (atom nil))
(if @style-node
  (goog.style/setStyles @style-node styles)
  (reset! style-node (goog.style/installStyles styles)))

(defn app []
  #_(str "HELLO "
         @(p/q '[:find ?n .
                 :where [?e]
                 [?e :user/name ?n]]
               db-conn)
         "!")
  (let [canvas-dom (atom nil)
        monet-canvas (atom nil)]
    [:div.container
     [:canvas.fill-parent
      {:ref (fn [e]      ; Called when node created and destroyed only
              (when e
                (reset! canvas-dom e)
                (reset! monet-canvas (canvas/init @canvas-dom "2d"))
                (canvas/add-entity @monet-canvas :background
                                   (canvas/entity {:x 0 :y 0 :w (:width @window) :h (:height @window)} ; val
                                                  nil ; update function
                                                  (fn [ctx val] ; draw function
                                                    (-> ctx
                                                        (canvas/fill-style "#f00")
                                                        (canvas/fill-rect val)))))))}]
     [:div.drawer.bg-aqua
      [:h2 {:style {:margin-left "2rem" :height "2rem"}} "Files in checkpoint"]
      [:div.file-menu
       (let [files ["file1.psd" "file2.psd" "file3.png" "file4.jpg" "file5.png" "file6.jpg"]]
         (for [f files]
           [:div.file-item.col {:key f} f]))]]]))

;;
;; Init
;;

(r/render [app] (js/document.getElementById "app"))

(client/start-router!)
