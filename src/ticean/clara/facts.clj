(ns ticean.clara.facts
  "Base system facts."
  (:require [ticean.clara.fact :refer [deffact]]))

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
