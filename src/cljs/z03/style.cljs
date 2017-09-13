(ns z03.style
  (:require
   [goog.style]
   [garden.core :refer [css]]
   [garden.units :as u]
   [garden.stylesheet :as stylesheet]
   [garden.color :as color :refer [hsl rgb]])
  (:require-macros
   [garden.def :refer [defkeyframes]]))

  
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
   [:textarea
    {:font-family ["Oswald" "sans-serif"]
     :font-weight 200
     :border {:style "solid" :width "0 0 0 2"}
     :background-color "#eee"}]
   [:.fill-parent {:width "100%" :height "100%"}]
   [:.lfloat {:float "left"}]
   [:.rfloat {:float "right"}]
   [:.center {:text-align "center"}]
   [:.nomargin {:margin 0 :padding 0}]
   [:.clickable {:cursor "pointer"}]
   [:.divider {:border {:style "solid" :width "0 0 1 0"}}]
   ;; Colors
   [:.bg-aqua {:background-color "#7fdbff"}]
   [:.aqua {:color "#7fdbff"}]
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
