(ns ticean.clara.shopping
  "Demonstration of Clara rules applied to a commerce application.

  This is extended from the Clara examples.
  https://github.com/cerner/clara-examples
  "
  (:require
    [clara.rules.accumulators :as acc]
    [clara.rules :refer [defquery defrule fire-rules insert insert!
                         mk-session query]]))


;;;; Fact Types

(defrecord ActiveShippingMethod [id name label description rate group carrier attributes])
(defrecord Customer [status])
(defrecord Discount [code name description type value])
(defrecord Order [year month day shipping-address])
(defrecord OrderLineItem [sku cost attributes])
(defrecord OrderPromoCode [code])
(defrecord OrderTotal [value])
(defrecord Promotion [code name description type config])
(defrecord ShippingMethod [id name label description rate group carrier attributes])
(defrecord ShippingRestriction [sku code description])


;;;; Base rules and queries.

(defrule order-total
  [?value <- (acc/sum :cost) :from [OrderLineItem]]
  =>
  (insert! (->OrderTotal ?value)))

(defquery get-order-total
  "Query to find the order total."
  []
  (?value <- OrderTotal))

(def max-discount
  "Accumulator that returns the highest percentage discount."
  ;; Note that this currently assumes percent!!
  (acc/max :value :returns-fact true))

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
