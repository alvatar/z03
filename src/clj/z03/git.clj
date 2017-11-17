(ns z03.git
  (:require [clojure.pprint :refer [pprint]]
            [clojure.java.io :as io]
            [environ.core :refer [env]]
            [clj-ssh.ssh :as ssh]
            ;; -----
            [z03.utils :as utils]))

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

(defn do-ssh-send [session cmd & [out-type]]
  (->> (-> (ssh/ssh session {:in cmd})
           :out
           (clojure.string/split #"\n"))
       (drop 2)
       (map clojure.string/trim)))

(defn do-ssh-send-with-bytes [session cmd]
  (-> (ssh/ssh session {:in cmd :out :bytes})
      :out))

(defn ssh-send [user-id cmd & [bytes?]]
  (if-let [session (get @session-registry user-id)]
    (if bytes?
      (do-ssh-send-with-bytes session cmd)
      (do-ssh-send session cmd))
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

(defn get-commits [user-id repo-dir & [{:keys [reverse]}]]
  (let [results
        (ssh-send user-id
                  (format "cd %s && git rev-list --no-max-parents --all %s --color=never --format=format:\"%%H<>%%P<>%%ar<>%%an<>%%s\""
                          repo-dir
                          (if reverse "--reverse" "")))]
    (for [[_ line] (partition 2 results)]
      (let [[hash parents age author subject] (clojure.string/split line #"<>")]
        {:hash (re-find #"\w+" hash)
         :parents (keep not-empty (clojure.string/split parents #"\s+"))
         :age age
         :author author
         :subject subject}))))

(defn list-dir [user-id repo-dir dir git-ref]
  (let [results
        (ssh-send user-id
                  (format "cd %s && git ls-tree --name-only %s ./%s/ | while read file; do git --no-pager log -n 1 --pretty=\"$file*%%ar*%%s\" -- $file && file -b $file; done"
                          repo-dir
                          git-ref
                          dir))]
    (into {}
          (for [[line filetype] (partition 2 results)]
            (let [[filename age & [subject]] (clojure.string/split line #"\*")]
              [filename {:filetype filetype
                         :subject subject
                         :age age}])))))

(defn get-tree [user-id repo-dir git-ref]
  (let [results
        (ssh-send user-id
                  (format "cd %s && git ls-tree --name-only -r %s ./ | while read file; do git --no-pager log -n 1 --pretty=\"$file*%%ar*%%s\" -- $file; done"
                          repo-dir
                          git-ref))
        tree (atom {})]
    (doseq [line results]
      (let [[pathstr age & [subject]] (clojure.string/split line #"\*")
            path (clojure.string/split pathstr #"/")]
        (if (= (count path) 1)
          (swap! tree assoc pathstr {:subject subject :age age :full-path pathstr})
          (swap! tree update-in (butlast path) assoc (last path) {:subject subject :age age :full-path pathstr}))))
    @tree))

(defn get-refs [user-id repo-dir]
  (let [lines (ssh-send user-id (str "cd " repo-dir " && git for-each-ref"))]
    (into {}
          (keep (fn [line]
                  (let [[commit _ ref] (clojure.string/split line #"\s+")
                        [_ heads? name] (clojure.string/split ref #"/")]
                    (when (= heads? "heads") [commit name])))
                lines))))

(defn get-fork-points [user-id repo-dir & [refs]]
  (into {}
        (for [[_ branch] (or refs (get-refs user-id repo-dir))]
          [branch
           (first (ssh-send user-id
                            (format "cd %s && git merge-base --fork-point %s"
                                    repo-dir
                                    branch)))])))

(defn get-file [user-id repo-dir commit file & [tmp-dir]]
  (let [filename (.getFileName (java.nio.file.Paths/get "" (into-array String [file])))
        target-filename (str (or tmp-dir "/tmp") "/" filename "-" (utils/rand-str 15))]
    (ssh-send user-id
              (format "cd %s && git --no-pager show %s:%s > %s"
                      repo-dir
                      commit
                      file
                      target-filename))
    target-filename))

(defn ensure-removal [get-file f]
  (let [filename (ref nil)
        output (ref nil)]
    (dosync
     (try
       (ref-set filename (get-file))
       (println @filename)
       (ref-set output (f @filename))
       (finally
         (when-let [file (io/as-file @filename)]
           (when (.exists file)
             (println "REMOVE.. " @filename)
             (io/delete-file @filename))))))
    @output))

(defn with-git-file [user-id repo-dir commit file f]
  (ensure-removal #(get-file user-id repo-dir commit file) f))

;; (defn search-in-array [arr c n]
;;   (let [len (count arr)]
;;     (loop [i 0
;;            ni 1]
;;       (cond (= (aget arr i) c)
;;             (if (= ni n) i (recur (inc i) (inc ni)))
;;             (>= i len) nil
;;             :else (recur (inc i) ni)))))

;; (def aaaa (get-file 1 "/data/Dropbox/projects/z03" "master" "./resources/public/img/template_ios7.png"))
;; (def bbbb (java.util.Arrays/copyOfRange aaaa 389 (count aaaa)))
;; (with-open [out (io/output-stream (io/file "tetsttt"))] (.write out bbbb))
;; (search-in-array aaaa 10 1)

;; Requires \r\n to \n conversion
