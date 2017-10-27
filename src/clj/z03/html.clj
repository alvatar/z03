(ns z03.html
  (:require [clojure.pprint :refer [pprint]]
            [environ.core :refer [env]]
            [hiccup.core :refer :all]
            [garden.core :refer [css]]
            [garden.units :as u]
            [garden.selectors :as s]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            ;; -----
            [z03.style :refer [styles]]))

(defn static-css []
  (css
   [:.nav-bar {:position "fixed"
               :width "100%" :height "35px"
               :background-color "black"}
    [:h4 {:color "white"
          :margin {:top "1px" :right "50px"}}]]
   [:.main-logo {:position "absolute"
                 :bottom "25%" :left "18%"}
    [:h4 {:margin-left "5px"}]]
   [:.main-contents {:position "absolute" :top "100%" :width "100%"
                     :border {:top "1px solid"}}]
   [:#login {:margin {:top "100px"} :height "600px"}
    [:h5 {:margin {:top (u/px 5) :bottom (u/px 5)}}]
    [(s/input (s/attr :type=submit)) {:margin {:top (u/px 60)}}]]
   [:.login-logo {:margin {:top (u/px 20)
                           :left (u/px 25)}}]))

(defn head []
  [:head
   [:meta {:charset "utf-8"}]
   [:title "z03"]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
   ;; Normalize
   [:link {:rel "stylesheet" :href "https://necolas.github.io/normalize.css/7.0.0/normalize.css"}]
   ;; Awesome Font
   [:link {:rel "stylesheet" :href "https://maxcdn.bootstrapcdn.com/font-awesome/4.7.0/css/font-awesome.min.css"}]
   ;; Gridlex
   [:link {:rel "stylesheet" :href "https://cdnjs.cloudflare.com/ajax/libs/gridlex/2.4.0/gridlex.min.css"}]
   [:style (str styles "\n\n" (static-css))]])

(defn login-content []
  [:div#login
   [:form {:method "post" :action "/login"}
    (anti-forgery-field)
    [:div
     [:div.center [:h5 "User"]]
     [:div.center [:input.flat-text-field {:type "text" :name "user"}]]]
    [:div
     [:div.center [:h5 "Password"]]
     [:div.center [:input.flat-text-field {:type "password" :name "password"}]]]
    [:div.center
     [:input.flat-button {:type "submit" :value "login"}]]]])

(defn login []
  (html
   [:html
    (head)
    [:body
     [:div.login-logo
      [:img {:src "svg/logo.svg" :width "90px"}]]
     (login-content)]]))

(defn index []
  (html
   [:html
    (head)
    [:body
     [:div.nav-bar
      [:h4.rfloat [:a.white {:href "#login"} "Login"]]]
     [:div.main-logo
      [:img {:src "svg/logo.svg"}]
      [:h4 "Version Control for Graphic Designers"]]
     [:div.main-contents (login-content)]]]))

(defn- html-template [js]
  (html
   [:html
    (head)
    [:body
     [:div#app]
     [:script {:src js :type "text/javascript"}]]]))

(defn user-home [id]
  (html-template "../js/compiled/z03.js"))

(defn presenter []
  (html-template "js/compiled/z03-presenter.js"))
