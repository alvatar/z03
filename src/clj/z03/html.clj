(ns z03.html
  (:require [clojure.pprint :refer [pprint]]
            [hiccup.core :refer :all]
            [garden.core :refer [css]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]))

(def common-css
  (css []))

(def head
  [:head
   [:meta {:charset "utf-8"}]
   [:title "z03"]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
   ;; Normalize
   [:link {:rel "stylesheet" :href "https://necolas.github.io/normalize.css/7.0.0/normalize.css"}]
   ;; Awesome Font
   [:link {:rel "stylesheet" :href "https://maxcdn.bootstrapcdn.com/font-awesome/4.7.0/css/font-awesome.min.css"}]
   ;; Gridlex
   [:link {:rel "stylesheet" :href "https://cdnjs.cloudflare.com/ajax/libs/gridlex/2.4.0/gridlex.min.css"}]])

(defn- html-template [js]
  (html
   [:html
    head
    [:body
     [:div#app]
     [:script {:src js :type "text/javascript"}]]]))

(defn user-home [id]
  ;;(html-template "js/compiled/z03.js")
  (html
   [:html
    head
    [:body
     [:h1 "Hi, " id]
     [:form {:action "/logout" :method "post"}
      (anti-forgery-field)
      [:input {:type "submit" :value "logout"}]]]]))

(def presenter
  (html-template "js/compiled/z03-presenter.js"))

(defn login []
  (html
   [:html
    head
    [:body
     [:div
      [:form {:method "post"}
       (anti-forgery-field)
       [:div
        [:div.centered.white "My user"]
        [:div.centered [:input {:type "text" :name "user"}]]]
       [:div
        [:div.centered.white "My password"]
        [:div.centered [:input {:type "password" :name "password"}]]]
       [:input {:type "submit" :value "login"}]]]]]))
