(ns ticean.clara.session
  "Clara session helpers."
  (:require
    [clara.rules :as clara]
    [clara.tools.inspect :as inspect]
    [ticean.clara.shopping :as shopping])
  (:import
    [ticean.clara.shopping ActiveShippingMethod Customer Order OrderPromoCode
       OrderLineItem OrderTotal Discount Promotion ShippingMethod
       ShippingRestriction]))

(defn print-parsed-rules [rules print?]
  (when print?
    (println "\nPARSED RULES: \n")
    (clojure.pprint/pprint rules)
    println "\n\n")
  rules)

(defn print-explain-activations [session print?]
  (when print?
    (println "\nEXPLAIN ACTIVATIONS: \n")
    (inspect/explain-activations session)
    (println "\n\n"))
  session)

(defn calculate
  "Reduces over the session to build a results maps."
  [session]
  (let [order-line-item-subtotal
        (-> session (clara/query shopping/get-order-line-item-subtotal)
            first :?value :value)
        order-shipping-rate-subtotal
        (-> session (clara/query shopping/get-order-shipping-rate-subtotal)
            first :?value :value)
        order-shipping-surcharge-subtotal
        (-> session (clara/query shopping/get-order-shipping-surcharge-subtotal)
            first :?value :value)
        promotions
        (map :?promotion (clara/query session shopping/get-promotions))
        discounts
        (map :?discount (clara/query session shopping/get-all-discounts))
        active-shipping-methods
        (map :?active-shipping-method
             (clara/query session shopping/get-active-shipping-methods))
        validated-shipping-method
        (-> session (clara/query shopping/get-validated-shipping-methods)
            first :?result)
        shipping-restrictions
        (map :?shipping-restriction
             (clara/query session shopping/get-shipping-restrictions))
        validation-errors
        (map :?error
             (clara/query session shopping/get-validation-errors))
        order-shipping-subtotal (+ order-shipping-rate-subtotal
                                   order-shipping-surcharge-subtotal)]
    {:totals
     {:order-line-item-subtotal order-line-item-subtotal
      :order-shipping-rate-subtotal order-shipping-rate-subtotal
      :order-shipping-surcharge-subtotal order-shipping-surcharge-subtotal
      :order-shipping-subtotal order-shipping-subtotal
      :order-grand-total (+ order-line-item-subtotal
                            order-shipping-subtotal)}
     :promotions promotions
     :discounts discounts
     :active-shipping-methods active-shipping-methods
     :validated-shipping-method validated-shipping-method
     :shipping-restrictions shipping-restrictions
     :validation-errors validation-errors}))

(defn base-session
  "Creates a base Clara session which includes common facts and rules."
  [facts rules]
  (-> (clara/mk-session 'ticean.clara.parser
                        'ticean.clara.shopping
                        rules)
      (clara/insert-all facts)
      (clara/fire-rules)))

(defn run-session
  "Clara sessions are immutable. Appends facts to the provided session and
  fires rules. Queries the session and returns cart information."
  [session & {:keys [facts explain-activations?]}]
  (-> session
      (clara/insert-all facts)
      (clara/fire-rules)
      (print-explain-activations explain-activations?)))
