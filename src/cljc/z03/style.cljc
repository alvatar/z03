(ns z03.style
  #?(:clj
     (:require
      [garden.core :refer [css]]
      [garden.units :as u]
      [garden.stylesheet :as stylesheet]
      [garden.color :as color :refer [hsl rgb]]
      [garden.def :refer [defkeyframes]]))
  #?(:cljs
     (:require
      [goog.style]
      [garden.core :refer [css]]
      [garden.units :as u]
      [garden.stylesheet :as stylesheet]
      [garden.color :as color :refer [hsl rgb]]))
  #?(:cljs
     (:require-macros
      [garden.def :refer [defkeyframes]])))

  
(defkeyframes drawer-animation-show
  [:from {:right (u/percent -50)}]
  [:to {:right 0}])

(defkeyframes drawer-animation-hide
  [:from {:right 0}]
  [:to {:right (u/percent -50)}])

(def dark-grey "#333")
(def light-grey "#bbb")

;; @keyframes sk-bounce {
;;   0%, 100% { 
;;     transform: scale(0.0);
;;     -webkit-transform: scale(0.0);
;;   } 50% { 
;;     transform: scale(1.0);
;;     -webkit-transform: scale(1.0);
;;   }
;; }

(defkeyframes spinner-bounce
  ["0%, 100%" {:transform "scale(0.0)"
               :-webkit-transform "scale(0.0)"}]
  ["50%" {:transform "scale(1.0)"
          :-webkit-transform "scale(1.0)"}])

(def spinner
  [spinner-bounce
   [:.spinner {:width (u/px 40)
               :height (u/px 40)
               :position "relative"}]
   [:.double-bounce1 :.double-bounce2 {:width (u/percent 100)
                                       :height (u/percent 100)
                                       :border-radius (u/percent 50)
                                       :background-color "#333"
                                       :opacity 0.6
                                       :position "absolute"
                                       :top 0
                                       :left 0
                                       :animation "spinner-bounce 2.0s infinite ease-in-out"}]
   [:.double-bounce2 {:animation-delay "-1.0s"}]])

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
     :line-height "1.2rem"
     :text-decoration 'none}]
   [:a:link {:text-decoration 'none}]
   [:a:hover {:text-decoration 'none}]
   [:a:active {:text-decoration 'none}]
   [:a:visited {:text-decoration 'none}]
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
   [:.center-aligner {:height "100%" :width "100%"
                      :display "flex" :align-items "center" :justify-content "center"}]
   ;; Elements
   [:.divider {:border {:style "solid" :width "0 0 1 0"}}]
   [:.flat-text-field {:background-color light-grey
                       :color dark-grey
                       :font-family ["Oswald" "sans-serif"]
                       :border "none"
                       :padding "10px 32px"
                       ;:text-align "center"
                       :text-decoration "none"
                       :display "inline-block"
                       :font-size "16px"}]
   [:.flat-button {:background-color light-grey
                   :color dark-grey
                   :cursor "pointer"
                   :font-family ["Oswald" "sans-serif"]
                   :border "none"
                   :padding "15px 32px"
                   :text-align "center"
                   :text-decoration "none"
                   :display "inline-block"
                   :font-size "16px"}]
   [:.file-icon {:float "left" :color "#ccc" :margin {:top (u/px 14) :right (u/px 7)}}]
   [:#graph-container {:min-height "230px"
                       :overflow "auto"
                       :margin-bottom "-10px"}]
   [:#commit-head {;;:position "sticky" :bottom 0 :left 0
                   :margin-top "10px"
                   :width "100%"
                   :height "52px"
                   :background-color "#e2e2e2"}
    [:h5 {:margin {:top 0 :left "10px" :right 0 :bottom 0}
          :padding "0"}]]
   [:.commit-tooltip {:position "fixed" :margin {:top "12px" :left "12px"}}]
   ;; Colors
   [:.white {:color "white"}]
   [:.bg-aqua {:background-color "#7fdbff"}]
   [:.aqua {:color "#7fdbff"}]
   ;; Animations
   drawer-animation-show
   drawer-animation-hide
   spinner
   ;; Project UI
   (let [drawer {:position "absolute" :top 0
                 :width "50%" :height "100%"}]
     [[:.drawer-show (merge drawer {:right 0
                                    :animation [[drawer-animation-show "1.5s"]]})]
      [:.drawer-hide (merge drawer {:right (u/percent -50)
                                    :animation [[drawer-animation-hide "1.0s"]]})]])
   [:.file-item {:padding "0 1rem 1rem 1rem"}]
   [:.container {:position "relative"
                 :margin "0 auto"
                 :width (u/px 900)}]
   [:.section-container {:position "absolute"
                         :top 0 :left 0
                         :min-height (u/px 280) :width "100%"}]
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
                        :background-position "top left"}]
   [:.footer {:margin {:top (u/px 200)}}
    [:h6 {:float "left" :margin {:top (u/px 0) :right (u/px 20)}}]]
   [:.files-listing-header {:border {:style "solid" :width "0 0 1 0"}
                            :margin {:bottom (u/px 0)}}
    [:h5 {:margin {:bottom (u/px 0)}}]
    [:.author {:margin {:left (u/px 7)}}]]
   ;; Presenter UI
   [:.presenter-container
    [:.file-list {:position "absolute" :top (u/rem 4)}]
    [:.file-item-container {:position "relative"
                            :height (u/px 300) :width (u/px 300)}]
    (let [margin 15]
      [:.file-item {:position "absolute"
                    :top (u/px margin) :left (u/px margin) :bottom (u/px margin) :right (u/px margin)
                    :border {:style "solid" :color "#999"}}])
    [:.file-thumbnail {:position "absolute"
                       :top 0 :left 0
                       :height (u/percent 100) :width (u/percent 100)
                       :filter "opacity(60%) sepia(100%)"}
     [:&:hover {:filter "opacity(100%)"}]]]))

#?(:cljs
   (defonce style-node (atom nil)))

#?(:cljs
   (if @style-node
     (goog.style/setStyles @style-node styles)
     (reset! style-node (goog.style/installStyles styles))))
