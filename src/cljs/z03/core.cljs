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
   [z03.globals :as globals :refer [display-type window app-state]]
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
                            (reset! (:refs app-state) refs)
                            (reset! (:fork-points app-state) fork-points)
                            (draw-git-graph))))

(def get-commit-files
  (build-standard-request :project/get-commit-files
                          (fn [{:keys [filetree]}]
                            (reset! (:files app-state) filetree))))

(defn logout [& [on-success-fn on-failure-fn]]
  (sente/ajax-lite "/logout"
                   {:method :post
                    :headers {:X-CSRF-Token (:csrf-token @client/chsk-state)}
                    :params {}}
                   (fn [ajax-resp]
                     (if (sente/cb-success? ajax-resp)
                       (when on-success-fn (on-success-fn))
                       (when on-failure-fn (on-failure-fn))))))

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
    (when-not (= ?user :taoensso.sente/nil-uid)
      (reset! (:user app-state) ?user))
    (get-user-initial-data)))

;;
;; UI Components
;;

(defc dialog
  [{:keys [actions height contents-height]} & contents]
  [:div.overlay
   [:div.center-aligner
    [:div.dialog {:style {:width "600px" :height (or height "400px")}}
     [:div {:style {:min-height (or contents-height "350px")  :width "100%"}}
      contents]
     (conj [:div {:style {:margin-right "1rem"}}
            (for [[i [elem params body]] (mapv vector (range) actions)]
              [elem (merge {:key (str "dialog-action-" i)} params) body])])]]])

(defc user-profile-dialog
  < r/reactive
  [show-settings*]
  (when (react show-settings*)
    (dialog {:actions [[:button.dialog-button.rfloat {:on-click #(reset! show-settings* false)} "Save"]
                       [:button.dialog-button.rfloat {:on-click #(reset! show-settings* false)} "Cancel"]
                       [:button.dialog-button.lfloat {:on-click (fn [] (logout #(utils/open-url "/" false)))} "Logout"]]
             :contents-height "350px"
             :height "400px"}
            [:div {:style {:padding "1rem"}}
             [:h2 "Active Plan"]
             [:div.divider {:style {:margin-top "-14px"}}]
             [:h4 "Beta " [:strong.clickable {:on-click #(js/alert "Beta plan is the only one currently available")} "(Change)"]]
             [:h2 "Payments"]
             [:div.divider {:style {:margin-top "-14px"}}]
             [:h4 "None"]
             [:h2 "Billing Info"]
             [:div.divider {:style {:margin-top "-14px"}}]
             [:h4 "None"]])))

(defc project-settings-dialog
  < r/reactive
  [show-settings*]
  (when (react show-settings*)
    (dialog {:actions [[:button.dialog-button.rfloat {:on-click #(reset! show-settings* false)} "Save"]
                       [:button.dialog-button.rfloat {:on-click #(reset! show-settings* false)} "Cancel"]]
             :contents-height "350px"
             :height "400px"}
            [:div {:style {:padding "1rem"}}
             [:h2 "Project Name"]
             [:div.divider {:style {:margin-top "-14px"}}]
             [:h4 (react (:active-project app-state))]
             [:h2 "Project Owner"]
             [:div.divider {:style {:margin-top "-14px"}}]
             [:h4 (:user-name (react (:user app-state)))]])))

(defc footer
  []
  [:div.footer
   [:div.col-4.center [:div.login-logo [:img {:src "/svg/logo.svg" :width "90px"}]]]
   [:h6 (gstring/format "© %d Metapen Ltd." (.getFullYear (js/Date.)))]
   [:h6 "Contact"]
   [:h6 "Terms"]
   [:h6 "Privacy"]])

(defcs home-ui-header
  < r/reactive
  (r/local false ::show-settings)
  [_state]
  (let [show-settings* (::show-settings _state)]
    [:div.header-container
     [:div.header-text
      [:div.lfloat.logo [:img {:src "/svg/logo.svg" :width "60px"}]]
      [:h4.rfloat.clickable {:on-click #(reset! show-settings* true)} (:user-name (react (:user app-state)))]
      (user-profile-dialog show-settings*)]]))

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

(defcs project-ui-header
  < r/reactive
  (r/local false ::show-user-profile)
  (r/local false ::show-project-settings)
  [_state]
  (let [show-user-profile* (::show-user-profile _state)
        show-project-settings* (::show-project-settings _state)]
    [:div.header-container
     [:div.header-text
      [:div.lfloat.logo [:img {:src "/svg/logo.svg" :width "60px"}]]
      [:div.lfloat {:style {:margin-right "0.5rem"}}
       #_[:i.fa.fa-undo {:aria-hidden "true"}]]
      [:h4.rfloat.clickable {:on-click #(reset! show-user-profile* true)} (:user-name (react (:user app-state)))]
      [:h4.rfloat.clickable {:on-click #(reset! show-project-settings* true)} "Project"]
      [:h4.rfloat.clickable {:on-click #(reset! (:active-project app-state) nil)} "Back"]
      (user-profile-dialog show-user-profile*)
      (project-settings-dialog show-project-settings*)]]))

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
      (doseq [{:keys [hash subject parents age author]} commits]
        (let [nparents (count parents)
              commit-data (clj->js {:message subject :sha1 hash :date age :author author
                                    :onClick (fn [commit]
                                               (let [hash (oget commit "sha1")]
                                                 (reset! (:files app-state) nil)
                                                 (get-commit-files {:project @(:active-project app-state)
                                                                    :commit hash})
                                                 (reset! (:active-commit app-state) commit)))})]
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
               [:div.file-item.col-9
                (when commit-is-ref
                  [:div.rfloat {:style {:line-height "40px"}}
                   [:i.fa.fa-trash.file-icon.clickable {:on-click #(js/alert "no implemented")
                                                        :aria-hidden "true"}]])]])
            (for [{:keys [filename filetype age subject full-path]} (sort-by :filename file-rows)]
              [:div.grid-noGutter {:key filename :style {:height "40px" :border-style "solid" :border-width "0 0 1 0" :border-color "#ccc"}}
               [:div.file-item.col-3.clickable {:on-click #(reset! (:active-file app-state) full-path)}
                [:i.fa.fa-file.file-icon {:aria-hidden "true"}]
                [:p.nomargin {:style {:line-height "40px" :height "40px" :color "#008cb7"}} filename]]
               [:div.file-item.col-7 [:p.nomargin {:style {:line-height "40px" :height "40px"}} subject]]
               [:div.file-item.col-2
                [:p.nomargin.lfloat {:style {:line-height "40px" :height "40px"}} age]
                (when commit-is-ref
                  [:div.rfloat {:style {:line-height "40px"}} [:i.fa.fa-trash.file-icon {:aria-hidden "true"}]])]])))]])
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
