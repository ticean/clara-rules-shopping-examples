(ns dev
  (:refer-clojure :exclude [test])
  (:require
    [clojure.java.io :as io]
    [clojure.repl :refer :all]
    [clojure.tools.namespace.repl :refer [refresh]]
    [duct.core :as duct]
    [duct.core.repl :as duct-repl]
    [eftest.runner :as eftest]
    [fipp.edn :refer [pprint] :rename {pprint fipp}]
    [eftest.runner :as eftest]
    [integrant.core :as ig]
    [integrant.repl :refer [clear halt go init prep reset]]
    [integrant.repl.state :refer [config system]]))

(duct/load-hierarchy)

(defn read-config []
  (duct/read-config (io/resource "dev.edn")))

(defn test []
  (eftest/run-tests (eftest/find-tests "test")))

(clojure.tools.namespace.repl/set-refresh-dirs "dev/src" "src" "test")

(when (io/resource "local.clj")
  (load "local"))

(integrant.repl/set-prep! (comp duct/prep read-config))
