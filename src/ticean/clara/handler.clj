(ns ticean.clara.handler
  "Web handlers for session endpoints."
  (:require
    [ataraxy.response :as response]
    [cheshire.core :as json]
    [duct.logger :refer [log]]
    [integrant.core :as ig]
    [ticean.clara :as engine]
    [ticean.clara.facts :as facts]))

(defmethod ig/init-key :ticean.clara.handler/index
  [_ {:keys [engine]}]
  (fn [{[_ payload] :ataraxy/result}]
    (let [facts (map facts/->fact-record payload)]
      [::response/ok (engine/query-session engine facts)])))
