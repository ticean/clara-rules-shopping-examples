(ns ticean.clara.facts)

;;;; Fact Types

(defrecord Customer [status])
(defrecord Discount [code name description type value])
(defrecord Order [timestamp shipping-address])
(defrecord OrderLineItem [sku cost attributes])
(defrecord OrderPromoCode [code])
(defrecord OrderLineItemSubtotal [value])
(defrecord OrderShippingRateSubtotal [value])
(defrecord OrderShippingSurchargeSubtotal [value])
(defrecord ShippingAddress [name address1 address2 city company phone region
                            postal country is-commercial is-billing-default
                            is-shipping-default metafields])

; Shipping Methods
(defrecord ActiveShippingMethod [id name label description rate group carrier attributes])
(defrecord ShippingMethod [id name label description rate group carrier attributes])
(defrecord ShippingSurcharge [id label description cost sku])
(defrecord ShippingRestriction [sku code description])
(defrecord SelectedShippingMethod [id])
(defrecord ValidatedShippingMethod [id name label description rate group carrier attributes])

(defrecord ValidationError [id description data])

; Promotions
(defrecord Promotion [id name description promotional-class type config])


(defn fact-record-type [m] (-> m :fact-type name symbol))
(defn- map->fact-type [m conversion-fn]
  (-> m conversion-fn (dissoc :fact-type)))

(defmulti ->fact-record
  "Converts a map representing a fact to a fact Record. Note that not all facts
  are insertable because they should be inferred by Clara."
  fact-record-type)

(defmethod ->fact-record 'Customer [m] (map->fact-type m map->Customer))
(defmethod ->fact-record 'Discount [m] (map->fact-type m map->Discount))
(defmethod ->fact-record 'Order [m] (map->fact-type m map->Order))
(defmethod ->fact-record 'OrderLineItem [m] (map->fact-type m map->OrderLineItem))
(defmethod ->fact-record 'OrderPromoCode [m] (map->fact-type m map->OrderPromoCode))
(defmethod ->fact-record 'ShippingAddress [m] (map->fact-type m map->ShippingAddress))
(defmethod ->fact-record 'ShippingMethod [m] (map->fact-type m map->ShippingMethod))
(defmethod ->fact-record 'SelectedShippingMethod [m] (map->fact-type m map->SelectedShippingMethod))
(defmethod ->fact-record :default [m]
  (throw (ex-info "Unknown fact type"
                  {:input-type (fact-record-type m)
                   :input m})))
