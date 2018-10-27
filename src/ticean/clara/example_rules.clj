(ns ticean.clara.example-rules
  "Example rules that are not part of the base system."
  (:require
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



(defrule order-promotion-10-dollars-off
  [Customer (= "vip" status)]
  =>
  (insert! (facts/->Promotion
             "vip-customers-ten-dollars-off-order"
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
  (retract! (facts/map->ActiveShippingMethod ?shipping)))

(defrule example-promotion-shipping-surcharge
  [Customer (= "vip" status)]
  =>
  (insert! (facts/->Promotion
             "remove-shipping-surcharges"
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
  (insert! (facts/->ShippingSurcharge nil nil nil ?surcharge nil)))

(defrule promotion-no-shipping-surcharge-when-customer-is-vip
  "Example of adding a surcharge to an item based on it's properties. Includes
  rule that does not apply the surcharge when a promotion exists that removes
  it."
  [Promotion (= "remove-shipping-surcharges" id)]
  [?surcharge <- ShippingSurcharge]
  =>
  (retract! ?surcharge))
