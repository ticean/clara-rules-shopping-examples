(ns ticean.clara.session
  "Demonstration of Clara rules applied to a commerce application.

  This is extended from the Clara examples.
  https://github.com/cerner/clara-examples
  "
  (:require
    [clara.rules :as clara]
    [clara.tools.inspect :as inspect]
    [ticean.clara.parser :as parser]
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
  (let [order-total
        (-> session (clara/query shopping/get-order-total) first :?value :value)
        promotions
        (map :?promotion (clara/query session shopping/get-promotions))
        discounts
        (map :?discount (clara/query session shopping/get-all-discounts))
        shipping-methods
        (map :?shipping-method
             (clara/query session shopping/get-shipping-methods))
        shipping-restrictions
        (map :?shipping-restriction
             (clara/query session shopping/get-shipping-restrictions))]
    (-> {}
      (assoc :order-total order-total)
      (assoc :promotions promotions)
      (assoc :discounts discounts)
      (assoc :shipping-methods shipping-methods)
      (assoc :shipping-restrictions shipping-restrictions))))


;; These rules may be stored in an external file or database.
(def example-rules
  "INSERT shipping_restriction no-explosives text-description-goes-here
     WHEN order_line_item.attributes.isExplosive = kaboom;
  INSERT shipping_restriction noship-expensive-things text-description-goes-here
     WHEN order_line_item.cost > 100;")

(def session-storage (atom nil))
(defn base-session
  "Creates a base Clara session which includes common facts and rules."
  [rules]
  (-> (clara/mk-session 'ticean.clara.parser
                        'ticean.clara.shopping
                        rules)
      (clara/fire-rules)))

(defn load-base-session
  [& {:keys [print-parsed-rules? rules]}]
  (print-parsed-rules rules print-parsed-rules?)
  (reset! session-storage (base-session rules)))

(defn run-forked-session
  "Clara sessions are immutable. Appends facts to the provided session and
  fires rules. Queries the session and returns cart information."
  [session & {:keys [explain-activations?]}]
  (-> session
      (clara/insert
        (shopping/->Customer :not-vip)
        (shopping/->Order 2018 :august 20 {})
        (shopping/->OrderLineItem :gizmo 20 {})
        (shopping/->OrderLineItem :widget 120 {})
        (shopping/->OrderLineItem :fizzbuzz 90 {:flammable? true})
        (shopping/->OrderLineItem "firecracker" 10 {:isExplosive "kaboom"})
        (shopping/->OrderLineItem "north-face-jacket" 10 {:brand "NorthFace"}))
      (clara/fire-rules)
      (print-explain-activations explain-activations?)
      (calculate)))

(comment
  ;; Without instrumentation printing.
  (require '[clara.rules :as clara])
  (require '[ticean.clara.parser :as parser])
  (require '[ticean.clara.session :as session])
  (require '[ticean.clara.shopping :as shopping])

  (session/load-base-session
    :rules (parser/load-user-rules session/example-rules))

  (time
    (clojure.pprint/pprint
     (session/run-forked-session
       @session/session-storage
       :explain-activations? true))))
