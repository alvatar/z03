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
     :overflow-x "hidden"}]
   [:h1 :h2 {:font-family ["Bitter" "sans-serif"]
             :font-weight 700}]
   [:.fill-parent {:width "100%" :height "100%"}]
   [:.lfloat {:float "left"}]
   [:.rfloat {:float "right"}]
   ;; Colors
   [:.bg-aqua {:background-color "#7fdbff"}]
   ;; Animations
   drawer-animation-show
   drawer-animation-hide
   ;; Components
   [:.header {:position "absolute" :left 0 :top 0
              :margin {:left (u/rem 0.5) :top (u/rem 0.5)}
              :font {:family ["Bitter" "sans-serif"]
                     :weight 400
                     :size (u/rem 0.7)}}]
   (let [drawer {:position "absolute" :top 0
                 :width "50%" :height "100%"}]
     [[:.drawer-show (merge drawer {:right 0
                                    :animation [[drawer-animation-show "1.5s"]]})]
      [:.drawer-hide (merge drawer {:right (u/percent -50)
                                    :animation [[drawer-animation-hide "1.0s"]]})]])
   [:.file-menu]
   [:.file-item {:padding "2rem 2rem 2rem 2rem"}]))

(defonce style-node (atom nil))
(if @style-node
  (goog.style/setStyles @style-node styles)
  (reset! style-node (goog.style/installStyles styles)))

(defn header []
  [:div.header
   [:div.lfloat {:style {:margin-right "0.5rem"}}
    [:i.fa.fa-undo {:aria-hidden "true"}]]
   [:div.lfloat
    @(p/q '[:find ?n .
            :where [?e]
            [?e :user/name ?n]]
          db-conn)]])

(defonce ui-state
  {:selected-revision (r/atom nil)})

(defn main []
  [:div
   [:div.revisions
    [:div.current-revision {:style {:position "absolute" :top "50%" :left "50%"
                                    :margin-left "-30px" :margin-top "-40px"
                                    :cursor "pointer"}
                            :on-click #(reset! (:selected-revision ui-state) "main")}
     [:div.lfloat {:style {:font-size "80px"}}
      [:i.fa.fa-dot-circle-o {:aria-hidden "true"}]]]
    [:div.revision {:style {:position "absolute" :top "50%" :left "30%"
                            :margin-left "-25px" :margin-top "-25px"}}
     [:div.lfloat {:style {:font-size "50px"}} [:i.fa.fa-circle {:aria-hidden "true"}]]]
    [:div.revision {:style {:position "absolute" :top "50%" :left "20%"
                            :margin-left "-25px" :margin-top "-25px"}}
     [:div.lfloat {:style {:font-size "50px"}} [:i.fa.fa-circle {:aria-hidden "true"}]]]
    [:div.revision {:style {:position "absolute" :top "50%" :left "5%"
                            :margin-left "-25px" :margin-top "-25px"}}
     [:div.lfloat {:style {:font-size "50px"}} [:i.fa.fa-circle {:aria-hidden "true"}]]]]
   [(if @(:selected-revision ui-state) :div.drawer-show.bg-aqua :div.drawer-hide.bg-aqua)
    [:h2 {:style {:margin-left "2rem" :height "2rem"}} "Files in checkpoint"]
    [:div.file-menu
     (let [files ["file1.psd" "file2.psd" "file3.png" "file4.jpg" "file5.png" "file6.jpg"]]
       (for [f files]
         [:div.file-item.col {:key f} f]))]]
   [header]])

(defn app []
  (let [canvas-dom (atom nil)
        monet-canvas (atom nil)]
    [:div.container
     [:canvas
      {:width (:width @window) :height (:height @window)
       :on-click #(reset! (:selected-revision ui-state) nil)
       :ref (fn [e]     ; Called when node created and destroyed only
              (when e
                (reset! canvas-dom e)
                (reset! monet-canvas (canvas/init @canvas-dom "2d"))
                (canvas/add-entity @monet-canvas :background
                                   (canvas/entity {:x (/ (:width @window) 2) :y (/ (:height @window) 2) :r 20}
                                                  nil
                                                  (fn [ctx val]
                                                    (-> ctx
                                                        (canvas/stroke-width 2)
                                                        (canvas/begin-path)
                                                        (canvas/move-to 0 (/ (:height @window) 2))
                                                        (canvas/line-to (/ (:width @window) 2) (/ (:height @window) 2))
                                                        (canvas/stroke)))))))}]
     [main]]))

;;
;; Init
;;

(r/render [app] (js/document.getElementById "app"))

(client/start-router!)
