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

(comment
  ;; Without instrumentation printing.
  (require '[clara.rules :as rules])
  (require '[ticean.clara :as clara])
  (require '[ticean.clara.parser :as parser])
  (require '[ticean.clara.session :as session])
  (require '[ticean.clara.shopping :as shopping])

  ;; These rules may be stored in an external file or database.
  (def example-rules
    "INSERT shipping_restriction no-explosives text-description-goes-here
       WHEN order_line_item.attributes.isExplosive = kaboom;
    INSERT shipping_restriction noship-expensive-things text-description-goes-here
       WHEN order_line_item.cost > 100;")

  (def example-base-facts
    [#_{:fact-type 'ShippingMethod :id "001" :name "Ground" :label "Ground" :description "3-7 business days" :rate 5 :group "ground" :carrier nil :attributes {}}
     #_{:fact-type 'ShippingMethod :id "040" :name "USPS Gift Card" :label "USPS Gift Card" :description "USPS Gift Card" :rate 6 :group "ground" :carrier "USPS" :attributes {}}
     #_{:fact-type 'ShippingMethod :id "virtual-giftcard-shipping" :name "Virtual Gift Card Shipping" :label "USPS Gift Card Shipping Method" :description "" :rate 6 :group "virtual" :carrier nil :attributes {}}
     #_{:fact-type 'ShippingMethod :id "021" :name "USPS" :label "USPS" :description "3-7 business days" :rate 10 :group "ground" :carrier "USPS" :attributes {}}
     #_{:fact-type 'ShippingMethod :id "002" :name "2-Day Express" :label "2-Day Express" :description "2-3 business days - order by noon EST to ship same day" :rate 20 :group "ground" :carrier nil :attributes {}}
     {:fact-type 'ShippingMethod :id "free" :name "Free 2-Day Shipping" :label "Free 2-Day Shipping" :description "Free shipping for orders over $49.00" :rate 0 :group "ground" :carrier nil :attributes {:order-total-min 49.00 :order-total-max 100000}}])

  (def base-facts (map shopping/->record example-base-facts))

  (session/load-base-session
    :facts base-facts
    :rules (parser/load-user-rules example-rules))


  (def example-cart-facts
    [{:fact-type 'Customer :status :not-vip}
     {:fact-type 'Order :year 2018 :month :october :day 22 :attributes {}}
     {:fact-type 'OrderLineItem :sku :gizmo :cost 20 :attributes {}}
     {:fact-type 'OrderLineItem :sku :widget :cost 120 :attributes {}}
     {:fact-type 'OrderLineItem :sku :fizzbuzz :cost 90 :attributes {:flammable? true}}
     {:fact-type 'OrderLineItem :sku "firecracker" :cost 10 :attributes {:isExplosive "kaboom"}}
     {:fact-type 'OrderLineItem :sku "north-face-jacket" :cost 10 :attributes {:brand "NorthFace"}}])

  (def cart-facts (map shopping/->record example-cart-facts))

  (def result
    (-> @session/session-storage
        (session/run-session :facts cart-facts :explain-activations? false)
        (session/calculate)))

  (clojure.pprint/pprint result)
  (map :id (:active-shipping-methods result)))
