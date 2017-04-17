(defproject org.panlex.api "0.1.0-SNAPSHOT"
  :description "PanLex API wrapper"
  :url "http://panlex.org/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clj-http-lite "0.3.0"]
                 [cheshire "5.7.0"]]
  :main ^:skip-aot org.panlex.api
  :profiles {:uberjar {:aot :all}})
