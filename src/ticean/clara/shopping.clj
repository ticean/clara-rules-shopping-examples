(ns ticean.clara.shopping
  (:require
    [clara.rules.accumulators :as acc]
    [clara.rules :refer [defquery defrule fire-rules insert insert!
                         mk-session query]]))


;;;; Facts used in the examples below.

(defrecord Order [year month day])
(defrecord Customer [status])
(defrecord Discount [code name description type value])
(defrecord Promotion [reason type])
(defrecord OrderLineItem [sku cost])
(defrecord OrderTotal [total])


;;;; Some example rules. ;;;;

(defrule total-purchases
  [?total <- (acc/sum :cost) :from [OrderLineItem]]
  =>
  (insert! (->OrderTotal ?total)))

;;;Discounts.
(defrule summer-special
  "Place an order in the summer and get 20% off!"
  [Order (#{:june :july :august} month)]
  =>
  (insert! (->Discount :summer-special "Summer special." "" :percent 20)))

(defrule vip-discount
  "VIPs get a discount on purchases over $100. Cannot be combined with any
  other discount."
  [Customer (= status :vip)]
  [OrderTotal (> total 100)]
  =>
  (insert! (->Discount :vip "VIP Discount" "" :percent 10)))

(def max-discount
  "Accumulator that returns the highest percentage discount."
  ;; Note that this currently assumes percent!!
  (acc/max :value :returns-fact true))

(defquery get-best-discount
  "Query to find the best discount that can be applied"
  []
  [?discount <- max-discount :from [Discount]])

;;; Promotions.
(defrule free-widget-month
  "All purchases over $200 in August get a free widget."
  [Order (= :august month)]
  [OrderTotal (> total 200)]
  =>
  (insert! (->Promotion :free-widget-month :widget)))

(defrule free-lunch-with-gizmo
  "Anyone who purchases a gizmo gets a free lunch."
  [OrderLineItem (= sku :gizmo)]
  =>
  (insert! (->Promotion :free-lunch-with-gizmo :lunch)))

(defquery get-promotions
  "Query to find promotions for the purchase."
  []
  [?promotion <- Promotion])


;;;; The section below shows this example in action. ;;;;

(defn print-discounts!
  "Print the discounts from the given session."
  [session]
  (println "Printing Discounts:")
  (doseq [{discount :?discount} (query session get-best-discount)]
    (println "   " discount))
  session)

(defn print-promotions!
  "Prints promotions from the given session"
  [session]
  (println "Printing Promotions:")
  (doseq [{promotion :?promotion} (query session get-promotions)]
    (println "   " promotion))
  session)

(defn run-examples
  "Function to run the above example."
  []
  (println "\nStarting Clara session 1.")
  (-> (mk-session 'ticean.clara.shopping :cache false)
      (insert (->Customer :vip)
              (->Order 2013 :march 20)
              (->OrderLineItem :gizmo 20)
              (->OrderLineItem :widget 20))

      (fire-rules)
      (print-discounts!))

  (println "\nStarting Clara session 2.")
  (-> (mk-session 'ticean.clara.shopping)
      (insert (->Customer :not-vip)
              (->Order 2013 :august 20)
              (->OrderLineItem :gizmo 20)
              (->OrderLineItem :widget 120)
              (->OrderLineItem :widget 90))
      (fire-rules)
      (print-discounts!)
      (print-promotions!))

  nil)
