(ns z03.database
  (:require [clojure.pprint :refer [pprint]]
            [environ.core :refer [env]]
            [clojure.java.jdbc :as sql]
            [postgre-types.json :refer [add-json-type add-jsonb-type]]
            [camel-snake-kebab.core :as case-shift]
            [clj-time.core :as time]
            [cheshire.core :as json]))

(add-json-type json/generate-string json/parse-string)
(add-jsonb-type json/generate-string json/parse-string)

;; References:
;; SQL
;; http://clojure-doc.org/articles/ecosystem/java_jdbc/using_sql.html
;; PostgreSQL/JSON
;; https://github.com/jeffpsherman/postgres-jsonb-clojure/blob/master/src/postgres_jsonb_clojure/core.clj

(def db (or (env :database-url)
            ;;"postgres://zapxeakmarafti:O9-vM29dzvG0g2Qo505pTMJTkg@ec2-54-75-233-92.eu-west-1.compute.amazonaws.com:5432/d1heam857nkip0?sslmode=require"
            "postgresql://localhost:5432/ikidz-dev"))

(println "Connecting to PostgreSQL:" db)

(defn connect-to-production []
  (def db "")
  (println "Connecting to PostgreSQL:" db))

(defn connect-to-local []
  (def db "postgresql://localhost:5432/ikidz-dev")
  (println "Connecting to PostgreSQL:" db))

;;
;; Utils
;;

(defn ->kebab-case [r] (reduce-kv #(assoc %1 (case-shift/->kebab-case %2) %3) {} r))

(defn ->snake_case [r] (reduce-kv #(assoc %1 (case-shift/->snake_case %2) %3) {} r))

(defn keyword->column [k] (case-shift/->snake_case (name k)))

(defn clj-time->sql-time [clj-time]
 (java.sql.Date. (.getMillis (.toDateTime clj-time))))

