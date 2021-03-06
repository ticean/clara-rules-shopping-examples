(ns ticean.clara.base-rules
  "Base rules and queries for the system."
  (:require
    [clara.rules.accumulators :as acc]
    [clara.rules :refer [defquery defrule fire-rules insert insert!
                         mk-session query retract!]]
    [ticean.clara.facts :as facts])
  (:import
    [ticean.clara.facts ActiveShippingMethod
                        Customer
                        Discount
                        Order
                        OrderLineItem
                        OrderLineItemSubtotal
                        OrderPromoCode
                        OrderShippingRateSubtotal
                        OrderShippingSurchargeSubtotal
                        Promotion
                        SelectedShippingMethod
                        ShippingMethod
                        ShippingRestriction
                        ShippingSurcharge
                        ValidatedShippingMethod
                        ValidationError]))


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
  (insert! (facts/->OrderLineItemSubtotal ?value)))

(defrule shipping-rate-subtotal
  [?value <- (acc/sum :rate) :from [ValidatedShippingMethod]]
  =>
  (insert! (facts/->OrderShippingRateSubtotal ?value)))

(defrule shipping-surcharge-subtotal
  [?value <- (acc/sum :cost) :from [ShippingSurcharge]]
  =>
  (insert! (facts/->OrderShippingSurchargeSubtotal ?value)))

(defrule add-validated-shipping-method
  "Ensures the selected shipping method is in the activated list. Adds a new
  fact with the complete active method."
  [SelectedShippingMethod (= ?selected-id id)]
  [?validated <- ActiveShippingMethod (= ?selected-id id)]
  =>
  (insert! (facts/map->ValidatedShippingMethod ?validated)))

(defrule add-validation-error-on-invalid-selection
  "Inserts a ValidationError if the shipping method is not in the activated
  list."
  [?selected <- SelectedShippingMethod (= ?selected-id id)]
  [:not [ActiveShippingMethod (= ?selected-id id)]]
  =>
  (insert! (facts/->ValidationError
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
  (insert! (facts/map->ActiveShippingMethod ?method)))
