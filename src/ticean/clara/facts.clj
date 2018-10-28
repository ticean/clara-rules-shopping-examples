(ns ticean.clara.facts)

(defn fact-record-type [m] (-> m :fact-type))
(defmulti ->fact-record
  "Converts a map representing a fact to a fact Record. Note that not all facts
  are insertable because they should be inferred by Clara."
  fact-record-type)

(defmethod ->fact-record :default [m]
  (throw (ex-info "Unknown fact type"
                  {:input-type (fact-record-type m)
                   :input m})))

(defmacro deffact
  "Macro to create a new fact type and for the engine.

  Defines a record for the provided symbol and also a multimethod that will
  convert a map to a record.

  This can be done manually as well. Ensure the multimethod dispatch value
  matches a stringified record name.

  (defrecord MyRecord [arg1 arg2 etc])
  (defmethod ->fact-record \"MyRecord\" [m] (map->MyRecord m))"
  [name params & ancestors]
  (when (not (instance? clojure.lang.Symbol name))
    (throw (IllegalArgumentException.
             "First argument to deffact must be a symbol")))
  (let [str-name# (str name)
        conversion-fn# (symbol (str "map->" name))]
    `(do
       (defrecord ~name ~params)
       (defmethod ->fact-record ~str-name# [m#]
         (dissoc (~conversion-fn# m#) :fact-type)))))


;;;; Fact Types

(deffact Customer [status])
(deffact Discount [code name description type value])
(deffact Order [timestamp shipping-address])
(deffact OrderLineItem [sku cost attributes])
(deffact OrderPromoCode [code])
(deffact OrderLineItemSubtotal [value])
(deffact OrderShippingRateSubtotal [value])
(deffact OrderShippingSurchargeSubtotal [value])
(deffact ShippingAddress [name address1 address2 city company phone region
                          postal country is-commercial is-billing-default
                          is-shipping-default metafields])

; Shipping Methods
(deffact ActiveShippingMethod [id name label description rate group carrier attributes])
(deffact ShippingMethod [id name label description rate group carrier attributes])
(deffact ShippingSurcharge [id label description cost sku])
(deffact ShippingRestriction [sku code description])
(deffact SelectedShippingMethod [id])
(deffact ValidatedShippingMethod [id name label description rate group carrier attributes])

; Promotions
(deffact Promotion [id name description promotional-class type config])

; Validation
(deffact ValidationError [id description data])
