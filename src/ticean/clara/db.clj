(ns ticean.clara.db
  "Stores facts and rules."
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [integrant.core :as ig]
    [ticean.clara.facts :as facts]))

(defprotocol FactDatastore
  "Facts storage abstraction."
  (load-facts
    [this]
    "Loads data from a storage and returns a seq of fact records."))

(defrecord EdnFactDatastore
  [config]
  FactDatastore
  (load-facts [this]
    (let [file (:resource-file config)
          data (-> file io/resource slurp edn/read-string)]
      (doall (map facts/->fact-record data)))))

(defmethod ig/init-key ::edn-fact-datastore
  [_ {:keys [config]}]
  (->EdnFactDatastore config))
