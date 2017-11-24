(ns z03.globals
  ;; (:require
  ;;  [datascript.core :as d])
  )

(goog-define *server-ip* "127.0.0.1")

(goog-define *env* "dev")
(goog-define *enable-mobile-dev* true)

;;
;; Database
;;

;; (def db-schema {:user/name {:db.unique :db.unique/identity}})
;; (def db-conn (d/create-conn db-schema))

;; ;; Globals will use eid 1
;; (d/transact! db-conn [[:db/add 1 :user/name "Alvatar"]])

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

(defonce app-state
  {:csrf-token (atom nil)
   :user (atom nil)
   :projects (atom nil)
   :commits (atom nil)
   :refs (atom nil)
   :fork-points (atom nil)
   :files (atom nil)
   :current-path (atom [])
   :active-project (atom nil)
   :active-commit (atom nil)
   :active-file (atom nil)})
