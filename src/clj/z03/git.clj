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
                       " && git rev-list --no-max-parents --all --color=never --format=format:\"%H<>%P<>%ar<>%an<>%s\""))]
    (for [[_ line] (partition 2 results)]
      (let [[hash parents age author subject] (clojure.string/split line #"<>")]
        {:hash (re-find #"\w+" hash)
         :parents (clojure.string/split parents #"\s+")
         :age age
         :author author
         :subject subject}))))

(defn list-dir [user-id repo-dir dir git-ref]
  (let [results
        (ssh-send user-id
                  (str "cd "
                       repo-dir
                       " && git ls-tree --name-only "
                       git-ref
                       " ./"
                       dir
                       "/ | while read file; do git --no-pager log -n 1 --pretty=\"$file*%ar*%s\" -- $file && file -b $file; done"))]
    (into {}
          (for [[line filetype] (partition 2 results)]
            (let [[filename age & [subject]] (clojure.string/split line #"\*")]
              [filename {:filetype filetype
                         :subject subject
                         :age age}])))))

(defn get-tree [user-id repo-dir git-ref]
  (let [results
        (ssh-send user-id
                  (str "cd "
                       repo-dir
                       " && git ls-tree --name-only -r "
                       git-ref
                       " ./ | while read file; do git --no-pager log -n 1 --pretty=\"$file*%ar*%s\" -- $file; done"))
        tree (atom {})]
    (doseq [line results]
      (let [[filename age & [subject]] (clojure.string/split line #"\*")
            path (clojure.string/split filename #"/")]
        (if (= (count path) 1)
          (swap! tree assoc filename {:subject subject :age age})
          (swap! tree update-in (butlast path) assoc (last path) {:subject subject :age age}))))
    @tree))

(defn get-refs [user-id repo-dir]
  (let [lines (ssh-send user-id (str "cd " repo-dir " && git for-each-ref"))]
    (into {}
          (keep (fn [line]
                  (let [[commit _ ref] (clojure.string/split line #"\s+")
                        [_ heads? name] (clojure.string/split ref #"/")]
                    (when (= heads? "heads") [commit name])))
                lines))))
