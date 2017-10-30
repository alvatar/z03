(ns z03.git
  (:require [clojure.pprint :refer [pprint]]
            ;; Environment and configuration
            [environ.core :refer [env]]
            [clj-ssh.ssh :as ssh]))

(def ssh-agent (ssh/ssh-agent {}))

(def ^:dynamic *ssh-username* (env :ssh-username))
(def ^:dynamic *ssh-password* (env :ssh-password))

;; (defn test-ssh []
;;   (let [session (ssh/session ssh-agent "localhost"
;;                              {:username *ssh-username*
;;                               :password *ssh-password*
;;                               :strict-host-key-checking :no})]
;;     (ssh/with-connection session
;;       (println (:out (ssh/ssh session {:in "echo hi"}))))))

(defn open-ssh []
  (let [session (ssh/session ssh-agent "localhost"
                             {:username *ssh-username*
                              :password *ssh-password*
                              :strict-host-key-checking :no})]
    (ssh/connect session)
    session))

(defn ssh-send [session cmd]
  (-> (ssh/ssh session {:in cmd})
      :out
      (clojure.string/split #"\n")
      (nth 2)
      clojure.string/trim))

(defn close-ssh [session]
  (ssh/disconnect session))
