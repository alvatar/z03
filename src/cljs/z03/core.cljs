(ns z03.core
  (:require
   [oops.core :refer [oget oset!]]
   [taoensso.encore :as encore :refer-macros (have have?)]
   [taoensso.timbre :as timbre :refer-macros (tracef debugf infof warnf errorf)]
   [cljs.core.async :as async :refer (<! >! put! take! chan)]
   [taoensso.sente :as sente :refer (cb-success?)]
   [taoensso.sente.packers.transit :as sente-transit]
   [datascript.core :as d]
   [rum.core :as r :refer [defc defcs react]]
   [monet.canvas :as canvas]
   [goog.style]
   [garden.core :refer [css]]
   [garden.units :as u]
   [garden.stylesheet :as stylesheet]
   [garden.color :as color :refer [hsl rgb]]
   [goog.string :as gstring]
   [goog.math :as gmath]
   [goog.math.Rect]
   [gitgraph]
   ;; -----
   [z03.style]
   [z03.viewer :refer [file-ui]]
   [z03.globals :as globals :refer [db-conn display-type window app-state]]
   [z03.utils :as utils :refer [log* log**]]
   [z03.client :as client])
  (:require-macros
   [garden.def :refer [defkeyframes]]))


(goog-define *is-dev* false)

(enable-console-print!)
(timbre/set-level! :debug)

;;
;; Actions
;;

(defn build-standard-request [id & [handler]]
  (fn [data & [cb]]
    (client/chsk-send!
     [id data] 30000
     (fn [resp]
       (if-not (and (sente/cb-success? resp) (= :ok (:status resp)))
         (do (log* resp)
             (js/alert (gstring/format "Ups... Error in %s ¯\\_(ツ)_/¯ Restart the app, please..." id)))
         (do (when handler (handler resp))
             (when cb (cb resp))))))))

(def get-user-initial-data
  (build-standard-request :user/get-initial-data
                          (fn [{:keys [projects]}]
                            (reset! (:projects app-state) projects))))

(declare draw-git-graph)

(def get-project-initial-data
  (build-standard-request :project/get-initial-data
                          (fn [{:keys [filetree commits refs fork-points]}]
                            (reset! (:files app-state) filetree)
                            (reset! (:commits app-state) commits)
                            #_(let [last-commit (last commits)]
                              ;;(reset! (:active-commit app-state) last-commit)
                              (reset! (:hover-commit app-state) last-commit))
                            (reset! (:refs app-state) refs)
                            (reset! (:fork-points app-state) fork-points)
                            (draw-git-graph))))

(def get-commit-files
  (build-standard-request :project/get-commit-files
                          (fn [{:keys [filetree]}]
                            (reset! (:files app-state) filetree))))

;;
;; Event Handlers
;;

(defmethod client/-event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (let [[old-state-map new-state-map] (have vector? ?data)]
    (when (:first-open? new-state-map)
      nil)))

(defmethod client/-event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?user ?csrf-token ?handshake-data] ?data]
    ;; (reset! (:user-id app-state) ?user)
    ;; (when-not (= ?user :taoensso.sente/nil-uid)
    ;;   (log* "HANDSHAKE"))
    (get-user-initial-data)))

;;
;; UI Components
;;

(defc footer
  < r/reactive
  []
  [:div.footer
   [:div.col-4.center [:div.login-logo [:img {:src "/svg/logo.svg" :width "90px"}]]]
   [:h6 (gstring/format "© %d Metapen Ltd." (.getFullYear (js/Date.)))]
   [:h6 "Contact"]
   [:h6 "Terms"]
   [:h6 "Privacy"]])

(defc home-ui-header
  < r/reactive
  []
  [:div.header-container
   [:div.header-text
    [:h4.lfloat.clickable {:on-click #(js/alert "User Settings (TODO)")} "Settings"]
    [:h4.rfloat.clickable
     "TODO"
     #_@(p/q '[:find ?n .
             :where [?e]
             [?e :user/name ?n]]
           db-conn)]]])

(defc home-ui
  < r/reactive
  []
  [:div.section-container
   (if-let [projects (react (:projects app-state))]
     [:div.project-list
      (for [{:keys [name description latest-commits]} projects]
        [:div.item.clickable {:key name :on-click (fn []
                                                    (reset! (:active-project app-state) name)
                                                    (get-project-initial-data {:name name}))}
         [:div {:style {:width "50%" :background-color "50%"}}
          [:h3 {:style {:margin-bottom "10px" :font-weight "bold"}} name]
          [:h4 {:style {:margin-top "10px"}} description]]
         [:div {:style {:position "absolute" :left "50%" :top 0 :margin-top "5rem"}}
          (for [[c idx] (map vector latest-commits (range))]
            [:h6 {:key (str name c) :style {:color "#555" :margin "0 0 0 0"}} "- " c])]])]
     [:div.center-aligner
      [:div.spinner [:div.double-bounce1] [:div.double-bounce2]]])
   (home-ui-header)])

(defn logout [& [on-success-fn on-failure-fn]]
  (sente/ajax-lite "/logout"
                   {:method :post
                    :headers {:X-CSRF-Token (:csrf-token @client/chsk-state)}
                    :params {}}
                   (fn [ajax-resp]
                     (if (sente/cb-success? ajax-resp)
                       (when on-success-fn (on-success-fn))
                       (when on-failure-fn (on-failure-fn))))))

(defc project-ui-header
  < r/reactive
  []
  [:div.header-container
   [:div.header-text
    [:div.lfloat {:style {:margin-right "0.5rem"}}
     ;; [:i.fa.fa-undo {:aria-hidden "true"}]
     [:h4.clickable {:on-click #(reset! (:active-project app-state) nil)} "Back"]]
    [:h4.lfloat.clickable {:on-click #(js/alert "Project Settings (TODO)")} "Settings"]
    [:h4.rfloat.clickable {:on-click (fn [] (logout #(utils/open-url "/" false)))} "Logout"]
    [:h4.rfloat
     "TODO"
     #_@(p/q '[:find ?n .
             :where [?e]
             [?e :user/name ?n]]
           db-conn)]]])

(defn draw-git-graph []
  (when-let [commits @(:commits app-state)]
    (let [gg (js/GitGraph. #js {"template" (js/GitGraph.Template.
                                            (clj->js {:colors ["#333" "#666" "#999"]
                                                      :branch {:lineWidth 4
                                                               :spacingX 40}
                                                      :commit {:spacingY -100
                                                               :dot {:size 7}
                                                               :tooltipHTMLFormatter (fn [commit]
                                                                                       (gstring/format "<h6 class=\"commit-tooltip\">%s (%s)</h6>"
                                                                                                       (oget commit "message")
                                                                                                       (oget commit "date")))}}))
                                "orientation" "horizontal"
                                "mode" "compact"})
          fork-points @(:fork-points app-state)
          branches (atom {})]
      #_(.addEventListener (oget gg "canvas")
                         "commit:mouseover"
                         (fn [ev]
                           (reset! (:hover-commit app-state)
                                   (let [{:keys [author date message sha1]} (clojure.walk/keywordize-keys (js->clj (oget ev "data")))]
                                     {:author author :age date :subject message :hash sha1}))))
      (doseq [{:keys [hash subject parents age author]} commits]
        (let [nparents (count parents)
              commit-data (clj->js {:message subject :sha1 hash :date age :author author
                                    :onClick (fn [commit]
                                               (let [hash (oget commit "sha1")]
                                                 (reset! (:files app-state) nil)
                                                 (get-commit-files {:project @(:active-project app-state)
                                                                    :commit hash})
                                                 (reset! (:active-commit app-state) commit
                                                         #_{:author (oget commit "author")
                                                            :age (oget commit "date")
                                                            :subject (oget commit "message")
                                                            :hash hash})))})]
          (case nparents
            0 (let [master (.branch gg "master")]
                (.commit master commit-data)
                (swap! branches assoc "master" {:head hash :gg-branch master}))
            1 (let [parent (first parents)
                    forks-here (filter (fn [[k v]] (= v parent)) fork-points)
                    [branch-name {:keys [head gg-branch]}] (first (filter (fn [[k {head :head}]] (= head parent)) @branches))]
                (when-not branch-name (js/alert "Error rendering graph: orphan commit"))
                (if (not-empty forks-here)
                  (let [fork-name (ffirst forks-here)] ; TODO: select the right fork
                    (if-let [already-branched (get @branches fork-name)] ; We've already branched, go back to where we branched from
                      (let [branched-from (:branched-from already-branched)
                            old-branch (get @branches branched-from)
                            gg-branch (:gg-branch old-branch)]
                        (swap! branches assoc branched-from {:head hash :gg-branch gg-branch})
                        (.commit gg-branch commit-data))
                      (let [new-gg-branch (.branch gg-branch fork-name)]
                        (swap! branches assoc fork-name {:head hash :gg-branch new-gg-branch :branched-from branch-name})
                        (.commit new-gg-branch commit-data))))
                  (do (.commit gg-branch commit-data)
                      (swap! branches assoc-in [branch-name :head] hash))))
            2 (js/alert "TODO: merge")
            (js/alert "Octopus merges not supported. What are you doing?"))))
      ;; Scroll to end
      (let [graph-div (js/document.getElementById "graph-container")]
        (oset! graph-div "scrollLeft" (oget graph-div "scrollWidth"))))))

;; Back to Hack
#_(defonce _clicked-commit (add-watch clicked-commit :clicked-commit
                                    (fn [key atom old-state new-state]
                                      (draw-git-graph))))
(defonce _draw-git-graph (atom nil))
(if @_draw-git-graph (js/setTimeout draw-git-graph 500) (reset! _draw-git-graph true))

(defc project-ui
  < r/reactive
  []
  (let [files (react (:files app-state))
        active-commit (react (:active-commit app-state))
        commit-is-ref (and active-commit
                           (some (let [h (oget active-commit "sha1")] #(= h %))
                                 (keys (react (:refs app-state)))))
        current-path (not-empty (react (:current-path app-state)))
        entries (mapv (fn [[k v]]
                        ;; This element in the structure is a string in the case of folders
                        (if (string? (first (first v)))
                          {:filename k :filetype "directory"}
                          {:filename k :filetype "file"
                           :subject (:subject v) :age (:age v) :full-path (:full-path v)}))
                      (if current-path (get-in files current-path) files))]
    [:div {:style {:padding-top "80px" :height "220px"}}
     [:div#graph-container
      [:canvas#gitGraph]
      (when active-commit
        [:div.commit-marker {:style {:left (oget active-commit "x")
                                     :top (oget active-commit "y")}}
         [:svg {:pointer-events "none" :height 70 :width 70} [:circle {:cx 50 :cy 50 :r 10 :stroke "#333" :stroke-width 3 :fill-opacity 0.0}]]
         (if commit-is-ref
           [:div.commit-actions-container
            [:h6 "Working version"]
            [:button.graph-button "add file"]
            [:button.graph-button "new version" [:i.fa.fa-arrow-down {:style {:margin-left "4px" } :aria-hidden "true"}]]
            [:button.graph-button "new revision" [:i.fa.fa-arrow-right {:style {:margin-left "4px" } :aria-hidden "true"}]]]
           [:div.commit-actions-container
            [:h6 "No open comments"]
            [:button.graph-button "new version" [:i.fa.fa-arrow-down {:style {:margin-left "4px" } :aria-hidden "true"}]]])])]
     (if-not files
       [:div
        [:div.center-aligner
         [:div.spinner [:div.double-bounce1] [:div.double-bounce2]]]]
       [:div
        (if active-commit
          [:div.grid-noGutter.files-listing-header
           [:div.col-9 [:h5.author [:strong (oget active-commit "author")] " " (oget active-commit "message")]]
           [:div.col-3 [:h5.rfloat (oget active-commit "date")]]]
          [:h5 "Please select version in graph"])
        [:div.files-listing
         (let [grouped (group-by #(= (:filetype %) "directory") entries)
               directories (get grouped true)
               file-rows (get grouped false)]
           (concat
            (when current-path
              [[:div.grid-noGutter {:key "dir-up" :style {:height "40px" :border-style "solid" :border-width "0 0 1 0" :border-color "#ccc"}}
                [:div.file-item.col-3.clickable {:on-click #(swap! (:current-path app-state) butlast)}
                 [:p.nomargin {:style {:line-height "40px" :height "40px" :color "#025382"}} ".."]]]])
            (for [{:keys [filename]} (sort-by :filename directories)]
              [:div.grid-noGutter {:key filename :style {:height "40px" :border-style "solid" :border-width "0 0 1 0" :border-color "#ccc"}}
               [:div.file-item.col-3.clickable {:on-click #(swap! (:current-path app-state) concat [filename])}
                [:i.fa.fa-folder.file-icon {:aria-hidden "true"}]
                [:p.nomargin {:style {:line-height "40px" :height "40px" :color "#025382"}} (str filename "/")]]
               ;;[:div.file-item.col-7 [:p.nomargin {:style {:line-height "40px" :height "40px"}} subject]]
               ;;[:div.file-item.col-2 [:p.nomargin {:style {:line-height "40px" :height "40px"}} age]]
               ])
            (for [{:keys [filename filetype age subject full-path]} (sort-by :filename file-rows)]
              [:div.grid-noGutter {:key filename :style {:height "40px" :border-style "solid" :border-width "0 0 1 0" :border-color "#ccc"}}
               [:div.file-item.col-3.clickable {:on-click #(reset! (:active-file app-state) full-path)}
                [:i.fa.fa-file.file-icon {:aria-hidden "true"}]
                [:p.nomargin {:style {:line-height "40px" :height "40px" :color "#008cb7"}} filename]]
               [:div.file-item.col-7 [:p.nomargin {:style {:line-height "40px" :height "40px"}} subject]]
               [:div.file-item.col-2 [:p.nomargin {:style {:line-height "40px" :height "40px"}} age]]])))]])
     (project-ui-header)
     (footer)]))

(defc app
  < r/reactive
  []
  [:div.container
   (cond
     (react (:active-file app-state))
     (file-ui)
     (react (:active-project app-state))
     (project-ui)
     :else
     (home-ui))])

;;
;; Init
;;

(r/mount (app) (js/document.getElementById "app"))

(defonce _router (client/start-router!))
