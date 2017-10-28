(ns z03.globals
  (:require
   [reagent.core :as r]
   [datascript.core :as d]
   [posh.reagent :as p]))

(goog-define *server-ip* "127.0.0.1")

(goog-define *env* "dev")
(goog-define *enable-mobile-dev* true)

;;
;; Database
;;

(def db-schema {:user/name {:db.unique :db.unique/identity}})
(def db-conn (d/create-conn db-schema))
(p/posh! db-conn)

;; Globals will use eid 1
(p/transact! db-conn [[:db/add 1 :user/name "Alvatar"]])

;; (def selected-revision
;;   (p/q '[:find ?n .
;;          :where [?e]
;;          [?e :revision/id ?n]]
;;        db-conn))

;; (defn set-selected-revision [id]
;;   (p/transact! db-conn [[:db/add 1 :revision/id id]]))

;; (defn deselect-revision []
;;   (when-let [e @selected-revision]
;;     (p/transact! db-conn [[:db/retract 1 :revision/id e]])))

;;
;; UI
;;

(defonce window (atom {:width (aget js/window "innerWidth")
                       :height (aget js/window "innerHeight")}))

(defn width->display-type [width]
  (cond (<= width 568) :xs
        (<= width 768) :sm
        (<= width 1024) :md
        (<= width 1280) :lg
        :else :xl))

(defonce display-type (atom (width->display-type (:width @window))))

(defonce _resize-display
  (. js/window addEventListener "resize"
     (fn []
       (let [width (aget js/window "innerWidth")
             height (aget js/window "innerHeight")]
         (swap! window assoc :width width)
         (swap! window assoc :height height)
         (reset! display-type (width->display-type width))))))

;;
;; State
;;

(def projects
  [{:id "ios7-templates"
    :description "iOS7 Template System"
    :commits ["[master] First version"]}
   {:id "rick-interstellar-enterprises-branding"
    :description "Rick Interstellar Enterprises: branding"
    :commits ["[master] Branding v1" "Simplified logo; reduced number of colors"]}
   {:id "adventure-time-branding"
    :description "Adventure Time: Branding"
    :commits ["[master] Final version" "Modifications from developer meeting" "Changed specifications for printing" "New layout for letter" "Complete change of palette"]}
   {:id "futurama-web-design"
    :description "Futurama: Web Design"
    :commits ["[master] Final version" "Mockup v4" "Mockup v3" "Mockup v2" "Mockup v1"]}
   {:id "portfolio-web"
    :description "Personal Portfolio: Web"
    :commits ["[master] Add Adventure Time" "Add Futurama Web Design" "I like this version" "Simplify and clean up design" "All the things in place"]}])

(def revisions
  {:master {:description "[master] Branding v1"
            :tags ["branding" "logo" "milestone"]
            :files [{:file "logo.psd" :last-commit "[master] Branding v1"}
                    {:file "file1.psd" :last-commit "Simplified logo; reduced number of colors"}
                    {:file "file2.psd" :last-commit "[master] Branding v1"}
                    {:file "file3.png" :last-commit "[master] Branding v1"}
                    {:file "file4.jpg" :last-commit "[master] Branding v1"}
                    {:file "file5.png" :last-commit "[master] Branding v1"}
                    {:file "file6.jpg" :last-commit "[master] Branding v1"}]}
   :head1 {:description "Simplified logo; reduced number of colors"
           :tags ["logo" "tweak"]
           :files [{:file "logo.psd" :last-commit "Simplified logo; reduced number of colors"}
                   {:file "file1.psd" :last-commit "General structure for branding"}]}})

(defonce ui-state
  {:active-project (r/atom nil)
   :active-revision (r/atom nil)
   :active-file (r/atom nil)})
