(ns z03.server
  (:require [clojure.java.io :as io]
            [clojure.stacktrace :refer [print-stack-trace]]
            [clojure.pprint :refer [pprint]]
            [environ.core :refer [env]]
            [taoensso.timbre :as log]
            ;; Ring
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.util.response :refer [response redirect content-type]]
            [ring.middleware.defaults :refer :all]
            [ring.middleware.stacktrace :as trace]
            [prone.middleware :as prone]
            [compojure.core :refer [ANY GET PUT POST DELETE defroutes]]
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
            ;; Internal
            [z03.actions :as actions]
            [z03.html :as html])
  (:import (java.lang.Integer)
           (java.net InetSocketAddress))
  (:gen-class))

;;
;; Sente setup
;;

(let [packer (sente-transit/get-transit-packer)
      {:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket! (get-sch-adapter) {:packer packer})]
  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def connected-uids connected-uids))

;;
;; HTTP Handlers
;;

(defmacro authenticated [f]
  `(fn [req#] (if-not (authenticated? req#) (throw-unauthorized) ~f)))

(defn login-handler [req]
  (let [{:keys [session params]} req
        {:keys [user pass role]} params]
    (if-let [db-user "thor" #_(user/authenticate user pass (keyword role))]
      (redirect "/hello")
      (redirect "/bad"))))

(defn logout-handler [req]
  (let [{:keys [session params]} req]
    {:status 200 :session (dissoc session :uid)}))

(defn render-page [page]
  (fn [req]
    (-> (response page)
        (content-type "text/html; charset=utf-8"))))

(defn user-page [id req]
  (authenticated
   (render id req)))

(defroutes app
  (resources "/")
  (GET "/u/:id" [id :as req] (user-page id req))
  (GET "/view" req (render html/presenter req))
  (GET "/login" req (render (html/login) req))
  (POST "/login" req login-handler)
  (POST "/logout" req logout-handler)
  ;; Sente
  (GET "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post req))
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
             true (wrap-defaults (if (= (env :env) "production") secure-site-defaults site-defaults))
             (not= (env :env) "production") prone/wrap-exceptions
             ;; (wrap-with-logger :debug println)
             true wrap-gzip)
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
