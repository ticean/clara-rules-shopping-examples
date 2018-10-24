(ns ticean.clara
  "The Clara runtime engine."
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [integrant.core :as ig]
    [ticean.clara.session :as session]
    [ticean.clara.shopping :as shopping]
    [ticean.clara.parser :as parser]))

(defprotocol IEngine
  (run-session
    [this facts]
    [this facts opts])
  (query-session
    [this facts]
    [this facts opts]))

(defrecord Engine [base-facts base-rules base-session]
  IEngine
  (run-session [this facts] (run-session this facts {}))
  (run-session [this facts {:keys [explain-activations?]}]
    (session/run-session (:base-session this)
                         :facts facts
                         :explain-activations? explain-activations?))

  (query-session [this facts] (query-session this facts {}))
  (query-session [this facts opts]
    (let [session (run-session this facts opts)]
      (session/calculate session))))

(defmethod ig/init-key ::engine
  [_  {config :config}]
  (let [{:keys [base-facts base-rules]} config
        facts   (map shopping/->record base-facts)
        rules   (parser/load-user-rules base-rules)
        session (session/base-session facts rules)]
    (->Engine facts rules session)))
