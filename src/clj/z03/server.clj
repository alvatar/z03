(ns z03.server
  (:require [clojure.java.io :as io]
            [clojure.stacktrace :refer [print-stack-trace]]
            [clojure.pprint :refer [pprint]]
            ;; Ring
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.util.response :refer [response content-type]]
            ;; Compojure
            [compojure.core :refer [ANY GET PUT POST DELETE defroutes]]
            [compojure.route :refer [resources not-found]]
            [environ.core :refer [env]]
            ;; Logging
            [taoensso.timbre :as log]
            ;; Web
            [ring.middleware.defaults :refer :all]
            [ring.middleware.stacktrace :as trace]
            [aleph [netty] [http]]
            [compojure.route :as route]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.aleph :refer (get-sch-adapter)]
            [taoensso.sente.packers.transit :as sente-transit]
            ;; Internal
            [z03.actions :as actions]
            [z03.html :as html])
  (:import (java.lang.Integer)
           (java.net InetSocketAddress))
  (:gen-class))


;; Sente setup
(let [packer (sente-transit/get-transit-packer)
      {:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket! (get-sch-adapter) {:packer packer})]
  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def connected-uids connected-uids))

(defn serve-page [page]
  (fn [req]
    (-> (response page)
        (content-type "text/html; charset=utf-8"))))

(defroutes app
  (GET "/" req (serve-page html/home))
  (GET "/view" req (serve-page html/presenter))
  (resources "/")
  ;; Sente
  (GET "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post req))
  (not-found "Woooot? Not found!"))

;;
;; Middleware
;;

(defn wrap-exceptions [app]
  "Ring wrapper providing exception capture"
  (let [wrap-error-page
        (fn [handler]
          (fn [req]
            (try (handler req)
                 (catch Exception e
                   (try (do (print-stack-trace e 20)
                            (println "== From request: ==")
                            (pprint req))
                        (catch Exception e2
                          (println "Exception trying to log exception?")))
                   {:status 500
                    :headers {"Content-Type" "text/plain"}
                    :body "500 Internal Error."}))))]
    ((if (or (env :production)
             (env :staging))
       wrap-error-page
       trace/wrap-stacktrace)
     app)))

(defonce server (atom nil))

;; (onelog.core/set-debug!)

(defn start! [& [port ip]]
  ;; (log/set-level! :debug)
  (actions/start-sente-router! ch-chsk)
  (reset! server
          (aleph.http/start-server
           (-> app
               (wrap-defaults (assoc-in (if (env :production) secure-site-defaults site-defaults)
                                        [:params :keywordize] true))
               wrap-exceptions
               ;; (wrap-with-logger :debug println)
               wrap-gzip)
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
