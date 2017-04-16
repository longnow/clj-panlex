(ns org.panlex.api
  (:gen-class
    :name org.panlex.api
    :methods [#^{:static true} [query [String java.util.Map] java.util.Map]
              #^{:static true} [query [String String] java.util.Map]
              #^{:static true} [queryAll [String java.util.Map] java.util.Map]
              #^{:static true} [queryAll [String String] java.util.Map]
              #^{:static true} [getTranslations [String String String] java.util.List]
              #^{:static true} [getTranslations [String String String int] java.util.List]
              #^{:static true} [getDistanceTwoTranslations [String String String] java.util.List]
              #^{:static true} [getDistanceTwoTranslations [String String String int] java.util.List]])
  (:require [clj-http.lite.client :as client]
            [cheshire.core :refer :all]
            [clojure.walk :as walk]
            ;[clojure.java.data :refer [from-java]]
            )
  (:import (clojure.lang MapEntry PersistentArrayMap)))

(def version 2)

(def api-server "http://api.panlex.org")

(def urlbase (if (= version 2)
               (str api-server "/v2")
               api-server))

(defn query [ep params]
  (let
    [url (if (= (first ep) \/) (str urlbase ep) ep)]
    (parse-string
      (:body (client/post url {:body (generate-string params)})) true)))

(defn- merge-results [query-output new-query-output]
  (merge query-output
         {:result (reduce conj (:result query-output) (:result new-query-output))
          :resultNum (+ (:resultNum query-output) (:resultNum new-query-output))}))

(defn query-all [ep params]
  (let [offset-params (if (:offset params)
                        params
                        (assoc params :offset 0))]
    (loop [temp-params offset-params
           growing-query-output nil]
      (let [{:keys [resultNum] :as query-output} (query ep temp-params)
            final-query-output (if growing-query-output
                                 (merge-results growing-query-output query-output)
                                 query-output)
            new-params (merge temp-params
                              {:offset (+ (:offset temp-params) resultNum)}
                              (when (:limit temp-params)
                                {:limit (- (:limit temp-params) resultNum)}))]
        (if (or
              (< resultNum (:resultMax query-output))
              (and (:limit new-params) (<= (:limit new-params) 0)))
          final-query-output
          (recur new-params final-query-output))))))

(defn get-translations
  [expn start-lang end-lang & {:keys [distance limit] :or {distance 1 limit nil}}]
  (let [params (merge {:trans_txt expn
                       :trans_uid start-lang
                       :uid end-lang
                       :trans_distance distance
                       :include "trans_quality"
                       :sort "trans_quality desc"}
                      (when limit {:limit limit}))]
    (:result (query-all "/expr" params))))

(defn- clojure-y [form]
  (walk/keywordize-keys
    (walk/prewalk #(cond (or (.isArray (class %))
                             (instance? java.util.Collection %)) (into [] %)
                          (instance? java.util.Map %) (into {} %)
                         :else %)
                  form)))

(defn- parse-params [params]
  (if (string? params)
    (parse-string params)
    (clojure-y params)))

(defn- java-query [f ep params]
  (walk/stringify-keys (f ep (parse-params params))))

(defn -query [ep params]
  (java-query query ep params))

(defn -queryAll [ep params]
  (java-query query-all ep params))

(defn -getTranslations
  ([expn start-lang end-lang]
   (walk/stringify-keys (get-translations expn start-lang end-lang)))
  ([expn start-lang end-lang limit]
   (walk/stringify-keys (get-translations expn start-lang end-lang :limit limit))))

(defn -getDistanceTwoTranslations
  ([expn start-lang end-lang]
    (walk/stringify-keys (get-translations expn start-lang end-lang :distance 2)))
  ([expn start-lang end-lang limit]
    (walk/stringify-keys (get-translations expn start-lang end-lang :distance 2 :limit limit))))

(defn -main
  "Translation test"
  [& args]
  (let [trans-result (apply get-translations args)]
    (doseq [entry trans-result]
      (println (str (:trans_quality entry) "\t" (:txt entry))))))