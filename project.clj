(defproject z03 "0.1.0-SNAPSHOT"
  :description "Z03"
  :url "http://z03.org"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.946" :scope "provided"]
                 [com.cognitect/transit-clj "0.8.300"]
                 [com.cognitect/transit-cljs "0.8.243"]
                 [environ "1.1.0"]
                 [ring "1.6.2"]
                 [ring/ring-defaults "0.3.1"]
                 [bk/ring-gzip "0.2.1"]
                 [ring.middleware.logger "0.5.0"]
                 [prone "1.1.4"]
                 [aleph "0.4.3"]
                 [compojure "1.6.0"]
                 [com.taoensso/encore "2.92.0"]
                 [com.taoensso/sente "1.11.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [pointslope/remit "0.3.0"]
                 [prone "1.1.4"]
                 [buddy/buddy-core "1.4.0"]
                 [buddy/buddy-auth "2.1.0"]
                 [buddy/buddy-hashers "1.3.0"]
                 [cheshire "5.8.0"]
                 [clj-time "0.14.0"]
                 ;; Database
                 [org.clojure/java.jdbc "0.7.3"]
                 [org.postgresql/postgresql "42.1.4"]
                 [postgre-types "0.0.4"]
                 ;; HTML
                 [hiccup "1.0.5"]
                 [garden "1.3.3"]
                 ;; Microlibraries
                 [camel-snake-kebab "0.4.0"]
                 [clojure-humanize "0.2.2"]
                 ;; Cljs
                 [reagent "0.8.0-alpha2"]
                 [datascript "0.16.2"]
                 [posh "0.5.5"]
                 [rm-hull/monet "0.3.0"]
                 [com.andrewmcveigh/cljs-time "0.5.1"]]

  :plugins [[lein-cljsbuild "1.1.3"]
            [lein-environ "1.0.3"]]

  :min-lein-version "2.6.1"

  :source-paths ["src/clj" "src/cljs" "src/cljc"]

  :test-paths ["test/clj" "test/cljc"]

  :clean-targets ^{:protect false} [:target-path :compile-path "resources/public/js"]

  :uberjar-name "z03-server.jar"

  ;; Use `lein run` if you just want to start a HTTP server, without figwheel
  :main z03.server

  ;; nREPL by default starts in the :main namespace, we want to start in `user`
  ;; because that's where our development helper functions like (run) and
  ;; (browser-repl) live.
  :repl-options {:init-ns user}

  :cljsbuild {:builds
              [{:id "dev-z03"
                :source-paths ["src/cljs" "src/cljc"]
                :figwheel true
                ;; Alternatively, you can configure a function to run every time figwheel reloads.
                ;; :figwheel {:on-jsload "z03.core/on-figwheel-reload"}
                :compiler {:main z03.core
                           :asset-path "js/compiled/out/z03"
                           :output-to "resources/public/js/compiled/z03.js"
                           :output-dir "resources/public/js/compiled/out/z03"
                           :source-map-timestamp true}}
               {:id "min-z03"
                :source-paths ["src/cljs" "src/cljc"]
                :jar true
                :compiler {:main z03.core
                           :output-to "resources/public/js/compiled/z03.js"
                           :output-dir "target/z03"
                           :source-map-timestamp true
                           :optimizations :advanced
                           :pretty-print false}}
               ;; {:id "test"
               ;;  :source-paths ["src/cljs" "test/cljs" "src/cljc" "test/cljc"]
               ;;  :compiler {:output-to "resources/public/js/compiled/testable.js"
               ;;             :main z03.test-runner
               ;;             :optimizations :none}}
               {:id "dev-presenter"
                :source-paths ["src/cljs" "src/cljc"]
                :figwheel true
                ;; Alternatively, you can configure a function to run every time figwheel reloads.
                ;; :figwheel {:on-jsload "z03.core/on-figwheel-reload"}
                :compiler {:main z03.presenter
                           :asset-path "js/compiled/out/presenter"
                           :output-to "resources/public/js/compiled/z03-presenter.js"
                           :output-dir "resources/public/js/compiled/out/presenter"
                           :source-map-timestamp true}}
               {:id "min-presenter"
                :source-paths ["src/cljs" "src/cljc"]
                :jar true
                :compiler {:main z03.presenter
                           :output-to "resources/public/js/compiled/z03-presenter.js"
                           :output-dir "target/presenter"
                           :source-map-timestamp true
                           :optimizations :advanced
                           :pretty-print false}}]}
  ;; When running figwheel from nREPL, figwheel will read this configuration
  ;; stanza, but it will read it without passing through leiningen's profile
  ;; merging. So don't put a :figwheel section under the :dev profile, it will
  ;; not be picked up, instead configure figwheel here on the top level.
  :figwheel {;; :http-server-root "public"       ;; serve static assets from resources/public/
             ;; :server-port 3449                ;; default
             ;; :server-ip "127.0.0.1"           ;; default
             :css-dirs ["resources/public/css"]  ;; watch and update CSS
             ;; Instead of booting a separate server on its own port, we embed
             ;; the server ring handler inside figwheel's http-kit server, so
             ;; assets and API endpoints can all be accessed on the same host
             ;; and port. If you prefer a separate server process then take this
             ;; out and start the server with `lein run`.
             :ring-handler user/http-handler
             ;; Start an nREPL server into the running figwheel process. We
             ;; don't do this, instead we do the opposite, running figwheel from
             ;; an nREPL process, see
             ;; https://github.com/bhauman/lein-figwheel/wiki/Using-the-Figwheel-REPL-within-NRepl
             ;; :nrepl-port 7888
             ;; To be able to open files in your editor from the heads up display
             ;; you will need to put a script on your path.
             ;; that script will have to take a file path and a line number
             ;; ie. in  ~/bin/myfile-opener
             ;; #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"
             :server-logfile "log/figwheel.log"}

  :doo {:build "test"}

  :profiles {:dev
             {:dependencies [[figwheel "0.5.14"]
                             [figwheel-sidecar "0.5.14"]
                             [com.cemerick/piggieback "0.2.2"]
                             [org.clojure/tools.nrepl "0.2.13"]
                             [midje "1.8.3"]]
              :plugins [[lein-figwheel "0.5.8"]
                        [lein-doo "0.1.6"]
                        [lein-ancient "0.6.10"]
                        [lein-midje "3.1.3"]]
              :source-paths ["dev"]
              :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
              :env {:env "dev"}}
             :test
             {:dependencies [[midje "1.8.3"]]
              :plugins [[lein-midje "3.1.3"]]
              :env {:env "test"}}
             :uberjar
             {:source-paths ^:replace ["src/clj" "src/cljc"]
              :prep-tasks ["compile" ["cljsbuild" "once" "min-z03" "min-presenter"]]
              :omit-source true
              :aot :all
              :env {:env "uberjar"}}})
