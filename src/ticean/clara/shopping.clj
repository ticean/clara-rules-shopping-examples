(ns ticean.clara.shopping
  "Demonstration of Clara rules applied to a commerce application."
  (:require
    [clara.rules.accumulators :as acc]
    [clara.rules :refer [defquery defrule fire-rules insert insert!
                         mk-session query]]))


;;;; Facts used in the examples below.

(defrecord Customer [status])
(defrecord Order [year month day shipping-address])
(defrecord OrderPromoCode [code])
(defrecord OrderLineItem [sku cost attributes])
(defrecord OrderTotal [total])

(defrecord Discount [code name description type value])
(defrecord Promotion [code name description type config])
(defrecord ShippingRestriction [sku code description])


;;;; Some example rules. ;;;;

(defrule order-total
  [?total <- (acc/sum :cost) :from [OrderLineItem]]
  =>
  (insert! (->OrderTotal ?total)))


;;;Discounts.

(defrule summer-discount
  "Place an order in the summer and get 20% off!"
  [Order (#{:june :july :august} month)]
  =>
  (insert! (->Discount :summer-discount
                       "Summer discount."
                       "Place an order in the summer and get 20% off."
                       :percent 20)))

(defrule vip-discount
  "VIPs get a discount on purchases over $100. Cannot be combined with any
  other discount."
  [Customer (= status :vip)]
  [OrderTotal (> total 100)]
  =>
  (insert! (->Discount :vip
                       "VIP Discount"
                       "VIPs get a 10% discount on purchases over $100."
                       :percent 10)))

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


;;; Promotions.

(defrule free-widget-month
  "All purchases over $200 in August get a free widget."
  [Order (= :august month)]
  [OrderTotal (> total 200)]
  =>
  (insert! (->Promotion :free-widget-month
                        "Free Widget Month"
                        "All purchases over $200 in August get a free widget."
                        :free-item
                        {:free-item-sku :widget})))

(defrule free-lunch-with-gizmo
  "Anyone who purchases a gizmo gets a free lunch."
  [OrderLineItem (= sku :gizmo)]
  =>
  (insert! (->Promotion :free-lunch-with-gizmo
                        "Free Lunch with Gizmo"
                        "Anyone who purchases a gizmo gets a free lunch."
                        :free-item
                        {:free-item-sku :lunch})))

(defquery get-promotions
  "Query to find promotions for the purchase."
  []
  [?promotion <- Promotion])


;;; Shipping restrictions.

(defrule noship-flammables
  [OrderLineItem (true? (:flammable? attributes)) (= ?sku sku)]
  =>
  (insert! (->ShippingRestriction
             ?sku
             :flammable
             "Flammable and hazardous materials are pickup-only.")))

(defrule noship-alcohol
  [OrderLineItem (true? (:alcohol? attributes)) (= ?sku sku)]
  [Order [{{state :state} :shipping-address}]
         (#{"AL" "OK" "UT"} state) (= ?state state)]
  =>
  (insert! (->ShippingRestriction
             ?sku
             :alcohol-shipping-restriction
             (str "Alcohol cannot be shipped to " ?state "."))))

(defquery get-shipping-restrictions
  "Query to find shipping restrictions for the purchase."
  []
  [?shipping-restriction <- ShippingRestriction])


;;;; The section below shows this example in action. ;;;;

(defn print-discounts!
  "Print the discounts from the given session."
  [session]
  (println "Printing Discounts:")
  (doseq [{best-discount :?best-discount} (query session get-best-discount)]
    (println "   Best Discount: " best-discount))
  (doseq [{discount :?discount} (query session get-all-discounts)]
    (println "   All Discounts: " discount))
  session)

(defn print-promotions!
  "Prints promotions from the given session"
  [session]
  (println "Printing Promotions:")
  (doseq [{promotion :?promotion} (query session get-promotions)]
    (println "   " promotion))
  session)

(defn print-shipping-restrictions!
  "Prints shipping restrictions from the given session"
  [session]
  (println "Printing Shipping Restrictions:")
  (doseq [{restriction :?shipping-restriction}
          (query session get-shipping-restrictions)]
    (println "   " restriction))
  session)

(defn run-examples
  "Function to run the above example."
  []

  (println "\nStarting Clara session 1.")
  (println "   Expects :summer-discount and and :vip-discount.")
  (println "   Expects :free-lunch-with-gizmo promotion.\n")
  (println "   Expects :alcohol shipping restriction\n")
  (-> (mk-session 'ticean.clara.shopping :cache false)
      (insert (->Customer :vip)
              (->Order 2018 :july 20 {:state "AL"})
              (->OrderLineItem :gizmo 20 {})
              (->OrderLineItem :widget 120 {})
              (->OrderLineItem :wine 120 {:alcohol? true}))
      (fire-rules)
      (print-discounts!)
      (print-promotions!)
      (print-shipping-restrictions!))

  (println "\nStarting Clara session 2.")
  (println "   Expects :summer-discount.\n")
  (println "   Expects :free-widget-month and :free-lunch-with-gizmo.\n")
  (println "   Expects :flammable shipping restriction\n"
    (-> (mk-session 'ticean.clara.shopping :cache false)
        (insert (->Customer :not-vip)
                (->Order 2018 :august 20 {})
                (->OrderLineItem :gizmo 20 {})
                (->OrderLineItem :widget 120 {})
                (->OrderLineItem :fizzbuzz 90 {:flammable? true}))
        (fire-rules)
        (print-discounts!)
        (print-promotions!)
        (print-shipping-restrictions!)))

  nil)
