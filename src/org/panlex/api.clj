(ns org.panlex.api
  (:gen-class
    :name org.panlex.api
    :methods [#^{:static true} [query [String java.util.HashMap] java.util.HashMap]
              #^{:static true} [queryAll [String java.util.HashMap] java.util.HashMap]
              #^{:static true} [getTranslations [String String String] "[Ljava.util.HashMap;"]
              #^{:static true} [getTranslations [String String String int] "[Ljava.util.HashMap;"]
              #^{:static true} [getTranslations [String String String int int] "[Ljava.util.HashMap;"]])
  (:require [clj-http.lite.client :as client]
            [cheshire.core :refer :all]
            [clojure.walk :as walk])
  (:import (clojure.lang PersistentVector)
           ))

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
  (let [offset-params (if (:offset params) params (assoc params :offset 0))]
    (loop [temp-params offset-params
           growing-query-output nil]
      (let [query-output (query ep temp-params)
            final-query-output (if growing-query-output
                                 (merge-results growing-query-output query-output)
                                 query-output)
            new-params (merge temp-params
                              {:offset (+ (:offset temp-params) (:resultNum query-output))}
                              (when (:limit temp-params)
                                {:limit (- (:limit temp-params) (:resultNum query-output))}))]
        (if (or
              (< (:resultNum query-output) (:resultMax query-output))
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

(defn- java-y [form]
  (walk/postwalk #(if (vector? %) (into-array %) %)
                 (walk/postwalk #(if (map? %) (java.util.HashMap. %) %)
                                (walk/stringify-keys form))))

(defn- clojure-y [form]
  (walk/keywordize-keys (walk/postwalk #(if (instance? java.util.HashMap %) (into {} %) %)
                 (walk/postwalk #(if (.isArray (class %)) (into [] %) %)
                                form))))


(defn -query [ep params] (query ep (clojure-y params)))

(defn -queryAll [ep params] (query-all ep (clojure-y params)))

(defn -getTranslations
  ([expn start-lang end-lang]
   (java-y (get-translations expn start-lang end-lang)))
  ([expn start-lang end-lang distance]
   (java-y (get-translations expn start-lang end-lang :distance distance)))
  ([expn start-lang end-lang distance limit]
   (java-y (get-translations expn start-lang end-lang :distance distance :limit limit))))


(defn -main
  "Translation test"
  [& args]
  (let [trans-result (apply get-translations args)]
    (doseq [entry trans-result]
      (println (str (:trans_quality entry) "\t" (:txt entry))))))