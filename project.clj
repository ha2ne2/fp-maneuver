(defproject fp-maneuver "1.4.0"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [seesaw "1.4.5"]
                 [environ "1.1.0"]
                 [org.clojure/data.json "0.2.6"]
                 [clj-http "3.6.1"]]
  :plugins [[lein-environ "1.1.0"]]
  :main ^:skip-aot fp-maneuver.core
  :target-path "target/%s"
  :profiles {:dev     {:env {:dev true}}
             :uberjar {:aot :all}})
