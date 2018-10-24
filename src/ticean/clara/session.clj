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
  (let [order-total
        (-> session (clara/query shopping/get-order-total) first :?value :value)
        promotions
        (map :?promotion (clara/query session shopping/get-promotions))
        discounts
        (map :?discount (clara/query session shopping/get-all-discounts))
        active-shipping-methods
        (map :?active-shipping-method
             (clara/query session shopping/get-active-shipping-methods))
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
      (assoc :active-shipping-methods active-shipping-methods)
      (assoc :shipping-methods shipping-methods)
      (assoc :shipping-restrictions shipping-restrictions))))

(def session-storage (atom nil))
(defn base-session
  "Creates a base Clara session which includes common facts and rules."
  [facts rules]
  (-> (clara/mk-session 'ticean.clara.parser
                        'ticean.clara.shopping
                        rules)
      (clara/insert-all facts)
      (clara/fire-rules)))

(defn load-base-session
  [& {:keys [facts print-parsed-rules? rules]}]
  (print-parsed-rules rules print-parsed-rules?)
  (reset! session-storage (base-session facts rules)))

(defn run-session
  "Clara sessions are immutable. Appends facts to the provided session and
  fires rules. Queries the session and returns cart information."
  [session & {:keys [facts explain-activations?]}]
  (-> session
      (clara/insert-all facts)
      (clara/fire-rules)
      (print-explain-activations explain-activations?)))
