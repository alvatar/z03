(ns z03.git
  (:require [clojure.pprint :refer [pprint]]
            ;; Environment and configuration
            [environ.core :refer [env]]
            [clj-ssh.ssh :as ssh]))

;;
;; SSH
;;

(def ssh-agent (ssh/ssh-agent {}))

(def ^:dynamic *git-username* (env :git-username))
(def ^:dynamic *git-password* (env :git-password))

;; (defn test-ssh []
;;   (let [session (ssh/session ssh-agent "localhost"
;;                              {:username *ssh-username*
;;                               :password *ssh-password*
;;                               :strict-host-key-checking :no})]
;;     (ssh/with-connection session
;;       (println (:out (ssh/ssh session {:in "echo hi"}))))))

(defonce session-registry (atom {}))

(defn open-ssh [user-id]
  (let [session (ssh/session ssh-agent "localhost"
                             {:username *git-username*
                              :password *git-password*
                              :strict-host-key-checking :no})]
    (ssh/connect session)
    (when (ssh/connected? session)
      (swap! session-registry assoc user-id session)
      session)))

(defn do-ssh-send [session cmd]
  (->> (-> (ssh/ssh session {:in cmd})
           :out
           (clojure.string/split #"\n"))
       (drop 2)
       (map clojure.string/trim)))

(defn ssh-send [user-id cmd]
  (if-let [session (get @session-registry user-id)]
    (do-ssh-send session cmd)
    (if-let [session (open-ssh user-id)]
      (do-ssh-send session cmd)
      :error-failed-to-connect)))

(defn close-ssh [user-id]
  (if-let [session (get @session-registry user-id)]
    (do (ssh/disconnect session)
        (swap! session-registry dissoc user-id))
    :error-session-not-found))

;;
;; Remote Git operations
;;

(defn get-commits [user-id repo-dir]
  (let [results
        (ssh-send user-id
                  (str "cd "
                       repo-dir
                       " && git --no-pager log --graph --format=format:'%H-%ar-%s-%an-%d' --all --color=never"))]
    (for [line results]
      (let [[hash age & rest] (clojure.string/split line #"-")]
        {:hash (re-find #"\w+" hash)
         :age age
         :message (first (butlast rest))
         :author (last rest)}))))

(defn get-files-info [user-id repo-dir git-ref]
  (let [results
        (ssh-send user-id
                  (str "cd "
                       repo-dir
                       " && git ls-tree --name-only "
                       git-ref
                       " | while read file; do git --no-pager log -n 1 --pretty=\"$file-%H-%ar\" -- $file && file -b $file; done"))]
    (for [[line filetype] (partition 2 results)]
      (let [all (clojure.string/split line #"-")
            reversed (reverse all)]
        {:filename (first (drop-last 2 all))
         :filetype filetype
         :age (first reversed)
         :last-commit (second reversed)}))))
