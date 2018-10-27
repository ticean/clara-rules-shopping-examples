(ns ticean.clara.shopping
  "Demonstration of Clara rules applied to a commerce application.

  This is extended from the Clara examples.
  https://github.com/cerner/clara-examples
  "
  (:require
    [clara.rules.accumulators :as acc]
    [clara.rules :refer [defquery defrule fire-rules insert insert!
                         mk-session query retract!]]))


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

;;;; Base rules and queries.

;; Accumulators
(def max-discount
  "Accumulator that returns the highest percentage discount."
  ;; Note that this currently assumes percent!!
  (acc/max :value :returns-fact true))


;;Queries

(defquery get-order-line-item-subtotal
  "Query to find the order total."
  []
  (?value <- OrderLineItemSubtotal))

(defquery get-order-shipping-rate-subtotal
  "Query to find the order shipping subtotal."
  []
  (?value <- OrderShippingRateSubtotal))

(defquery get-order-shipping-surcharge-subtotal
  "Query to find the order surcharge subtotal."
  []
  (?value <- OrderShippingSurchargeSubtotal))

(defquery get-orders
  "Query to get all orders."
  []
  (?order <- Order))

(defquery get-shipping-surcharges
  "Query to get all shipping surcharges."
  []
  (?surcharge <- ShippingSurcharge))

(defquery get-order-line-items
  "Query to get the order line items."
  []
  (?order-line-item <- OrderLineItem))

(defquery get-best-discount
  "Query to find the best discount that can be applied"
  []
  [?best-discount <- max-discount :from [Discount]])

(defquery get-all-discounts
  "Query to find the best discount that can be applied"
  []
  [?discount <- Discount])

(defquery get-promotions
  "Query to find promotions for the purchase."
  []
  [?promotion <- Promotion])

(defquery get-active-shipping-methods
  "Query to find all active shipping methods."
  []
  [?active-shipping-method <- ActiveShippingMethod])

(defquery get-shipping-methods
  "Query to find all configured shipping methods."
  []
  [?shipping-method <- ShippingMethod])

(defquery get-shipping-restrictions
  "Query to find shipping restrictions for the purchase."
  []
  [?shipping-restriction <- ShippingRestriction])

(defquery get-validated-shipping-methods
  "Query to find all validated shipping methods."
  []
  [?result <- ValidatedShippingMethod])

(defquery get-validation-errors
  "Query to find all validation errors."
  []
  [?error <- ValidationError])


;; Rules

(defrule order-line-item-subtotal
  [?value <- (acc/sum :cost) :from [OrderLineItem]]
  =>
  (insert! (->OrderLineItemSubtotal ?value)))

(defrule shipping-rate-subtotal
  [?value <- (acc/sum :rate) :from [ValidatedShippingMethod]]
  =>
  (insert! (->OrderShippingRateSubtotal ?value)))

(defrule shipping-surcharge-subtotal
  [?value <- (acc/sum :cost) :from [ShippingSurcharge]]
  =>
  (insert! (->OrderShippingSurchargeSubtotal ?value)))

(defrule add-validated-shipping-method
  "Ensures the selected shipping method is in the activated list. Adds a new
  fact with the complete active method."
  [SelectedShippingMethod (= ?selected-id id)]
  [?validated <- ActiveShippingMethod (= ?selected-id id)]
  =>
  (insert! (map->ValidatedShippingMethod ?validated)))

(defrule add-validation-error-on-invalid-selection
  "Inserts a ValidationError if the shipping method is not in the activated
  list."
  [?selected <- SelectedShippingMethod (= ?selected-id id)]
  [:not [ActiveShippingMethod (= ?selected-id id)]]
  =>
  (insert! (->ValidationError
             "invalid-selected-shipping-method"
             "The selected shipping method is not valid."
             {:selected ?selected})))

(defrule activate-shipping-by-order-total-limits
  "Activates shipping methods that conform to min and max of the total order
  amount. If the configuration values are not set on the ShippingMethod then
  assume that the method applies on the missing range type.

  WARNING: This is a naive implementation and uses the OrderLineItemSubTotal
  rather than the total AFTER all other discounts and promotions are applied.

  Assumes attributes which can be changed, of course.

    :order-total-min - The minimum total order spend for which the method will apply.
    :order-total-max - The maximum total order spend for which the method will apply."
  [?method <- ShippingMethod
   (= ?order-total-min (:order-total-min attributes))
   (= ?order-total-max (:order-total-max attributes))
   (if (number? ?order-total-min) (> ?value ?order-total-min) true)
   (if (number? ?order-total-max) (< ?value ?order-total-max) true)]
  [OrderLineItemSubtotal (= ?value value)]
  =>
  (insert! (map->ActiveShippingMethod ?method)))


;; Example Rules

(defrule order-promotion-10-dollars-off
  [Customer (= "vip" status)]
  =>
  (insert! (->Promotion "vip-customers-ten-dollars-off-order"
                        "Ten Dollars Off Your Order"
                        "Big discounts for VIP customers. Get $10 off any order."
                        "order"
                        nil
                        {})))

(defrule retract-virtual-giftcard-shipping-if-any-physical-products
  "Retract the virtual giftcard shipping method if any physical products."
  [OrderLineItem (not= "virtual-gift-card" sku) (= ?sku sku)]
  [?shipping <- ShippingMethod
   (= "virtual-giftcard-shipping" id)]
  =>
  (retract! (map->ActiveShippingMethod ?shipping)))

(defrule example-promotion-shipping-surcharge
  [Customer (= "vip" status)]
  =>
  (insert! (->Promotion "remove-shipping-surcharges"
                        ""
                        "Big discounts for VIP customers. No shipping surcharges on big items."
                        "order"
                        nil
                        {})))

(defrule add-shipping-surcharge
  "Example of adding a surcharge to an item based on it's properties. Includes
  rule that does not apply the surcharge when a promotion exists that removes
  it."
  [OrderLineItem (= ?surcharge (:shippingSurcharge attributes))
   (number? ?surcharge)]
  =>
  (insert! (->ShippingSurcharge nil nil nil ?surcharge nil)))

(defrule promotion-no-shipping-surcharge-when-customer-is-vip
  "Example of adding a surcharge to an item based on it's properties. Includes
  rule that does not apply the surcharge when a promotion exists that removes
  it."
  [Promotion (= "remove-shipping-surcharges" id)]
  [?surcharge <- ShippingSurcharge]
  =>
  (retract! ?surcharge))
