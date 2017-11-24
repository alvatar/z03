(ns z03.server
  (:require [clojure.java.io :as io]
            [clojure.stacktrace :refer [print-stack-trace]]
            [clojure.pprint :refer [pprint]]
            [environ.core :refer [env]]
            [taoensso.timbre :as log]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.util.response :refer [response redirect content-type file-response]]
            [ring.middleware.defaults :refer :all]
            [ring.middleware.stacktrace :as trace]
            [prone.middleware :as prone]
            [compojure.core :refer [ANY GET PUT POST DELETE defroutes context]]
            [compojure.route :refer [resources not-found]]
            [compojure.response :refer [render]]
            [aleph [netty] [http]]
            [compojure.route :as route]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.aleph :refer (get-sch-adapter)]
            [taoensso.sente.packers.transit :as sente-transit]
            [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.backends.session :refer [session-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            ;; -----
            [z03.actions :as actions]
            [z03.html :as html]
            [z03.database :as db]
            [z03.git :as git])
  (:import (java.lang.Integer)
           (java.net InetSocketAddress))
  (:gen-class))

;;
;; Sente setup
;;

(let [packer (sente-transit/get-transit-packer)
      {:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket! (get-sch-adapter) {:packer packer
                                                     :user-id-fn :identity})]
  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def connected-uids connected-uids))

;;
;; HTTP Handlers
;;

(defmacro authenticated [user f]
  `(fn [req#]
     (if-not (and (authenticated? req#)
                  (= (:user-name (get-in req# [:session :identity]))
                     ~user))
       (throw-unauthorized)
       ~f)))

(defn login-handler [req]
  (let [{:keys [session params]} req
        {:keys [user password]} params]
    (if-let [user-identity (select-keys (db/user-authenticate user password) [:id :user-name])]
      (let [updated-session (assoc session :identity user-identity)
            next-url (get-in req [:query-params "next"] (str "/u/" (:user-name user-identity)))]
        (assoc (redirect next-url) :session updated-session))
      (redirect (format "/login%s" (when-let [qs (:query-string req)] (str "?" qs)))))))

(defn logout-handler [req]
  (let [{:keys [session params]} req]
    (-> (redirect "/") (assoc :session (dissoc session :identity)))))

(defn user-home [user req]
  (authenticated user
                 (render (html/user-home user) req)))

(defn file-handler [user-id repo-dir commit file]
  (git/with-git-file
    user-id repo-dir commit file
    (fn [filename]
      {:status 200
       :headers {}
       :body (io/input-stream filename)})))

(def uploads-folder "/tmp/uploads/")

(defn send-files [{{file :file} :params :as req}]
  (let [copy-file (fn [{:keys [filename tempfile]}]
                    (let [local-file (str uploads-folder filename)]
                      (io/make-parents local-file)
                      (io/copy tempfile (io/file local-file))))]
    (if (vector? file)
      (dorun (map copy-file file))
      (copy-file file))
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body "OK"}))

(defroutes app
  (GET "/" req (render (html/index) req))
  (GET "/u" req #(redirect (or (when-let [u (:user-name (get-in % [:session :identity]))]
                                 (str "/u/" u))
                               "/login")))
  (GET "/u/:user" [user :as req] (user-home user req))
  (GET "/u/:user/:project" [user project :as req]
       (authenticated user
                      {:status 200
                       :headers {"Content-Type" "text/plain"}
                       :body (str {:user user :project project})}))
  (GET "/u/:user/:project/tree/:commit" [user project commit :as req]
       (authenticated user
                      {:status 200
                       :headers {"Content-Type" "text/plain"}
                       :body (str {:user user :project project :commit commit})}))
  (context "/u/:user/:project-name/blob/:commit" [user project-name commit file :as req]
           (GET "/*" []
                (authenticated user (when-let [project (db/project-get-by :name project-name)]
                                      (file-handler (get-in req [:session :identity :id])
                                                    (:git-repo project)
                                                    commit
                                                    (str "." (:path-info req)))))))
  (POST "/send-files" req (send-files req))
  #_(POST "/receive-file" 
        {{{tempfile :tempfile filename :filename} :img} :params :as params}
        (do (io/copy tempfile (io/file filename))
            {:status 200
             :headers {"Content-Type" "text/plain"}
             :body "OK"}))
  (GET "/view" req (render (html/presenter) req))
  (GET "/login" req (render (html/login) req))
  (POST "/login" req login-handler)
  (POST "/logout" req logout-handler)
  ;; Sente
  (GET "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post req))
  (resources "/")
  (not-found "Not found"))

;; (onelog.core/set-debug!)
(defonce server (atom nil))

(defn unauthorized-handler [request metadata]
  (if (authenticated? request)
    ;; If request is authenticated, raise 403 instead
    ;; of 401 (because user is authenticated but permission
    ;; denied is raised).
    (-> (render "Unauthorized user" request)
        (assoc :status 403))
    (redirect (format "/login?next=%s" (:uri request)))))

(def auth-backend (session-backend {:unauthorized-handler unauthorized-handler}))

(defn start! [& [port ip]]
  ;; (log/set-level! :debug)
  (actions/start-sente-router! ch-chsk)
  (reset! server
          (aleph.http/start-server
           (cond-> app
             (not= (env :env) "testing") (wrap-authorization auth-backend)
             (not= (env :env) "testing") (wrap-authentication auth-backend)
             true (wrap-defaults (-> (if (= (env :env) "production") secure-site-defaults site-defaults)
                                     (assoc :proxy true)))
             true wrap-gzip
             (not= (env :env) "production") prone/wrap-exceptions)
           {:port (Integer. (or port (env :port) 5000))
            :socket-address (if ip (new InetSocketAddress ip port))})))

(defn stop! []
  (when @server
    (.close @server)
    (reset! server nil)
    'stopped))

(defn -main [& [port ip]]
  (start! port ip)
  (aleph.netty/wait-for-close @server))
