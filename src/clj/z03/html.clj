(ns z03.html
  (:require [clojure.pprint :refer [pprint]]
            [hiccup.core :refer :all]
            [garden.core :refer [css]]))

(def common-css
  (css []))

(defn- html-template [js]
  (html
   [:html
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     ;; Normalize
     [:link {:rel "stylesheet" :href "https://necolas.github.io/normalize.css/7.0.0/normalize.css"}]
     ;; Awesome Font
     [:link {:rel "stylesheet" :href "https://maxcdn.bootstrapcdn.com/font-awesome/4.7.0/css/font-awesome.min.css"}]
     ;; Gridlex
     [:link {:rel "stylesheet" :href "https://cdnjs.cloudflare.com/ajax/libs/gridlex/2.4.0/gridlex.min.css"}]]
    [:body
     [:div#app]
     [:script {:src js :type "text/javascript"}]]]))

(def home
  (html-template "js/compiled/z03.js"))

(def presenter
  (html-template "js/compiled/z03-presenter.js"))
