(ns ticean.clara.parser
  "Demonstration of Clara rules applied to a commerce application.

  This is extended from the Clara examples.
  https://github.com/cerner/clara-examples
  "
  (:require
    [clara.rules :as clara]
    [clara.tools.inspect :as inspect]
    [instaparse.core :as insta]
    [ticean.clara.shopping :as shopping])
  (:import
    [ticean.clara.shopping ActiveShippingMethod Customer Order OrderPromoCode
       OrderLineItem OrderTotal Discount Promotion ShippingMethod
       ShippingRestriction]))

(defn shopping-grammar
  []
  (insta/parser
   "<RULES> = [DISCOUNT | PROMOTION | SHIPPING_RESTRICTION]+
    SHIPPING_RESTRICTION = <'insert shipping_restriction'> NAME DESCRIPTION <'WHEN'> CONDITION [<'and'> CONDITION]* <';'>;
    PROMOTION = <'insert promotion'> NAME PROMOTIONTYPE <'when'> CONDITION [<'and'> CONDITION]* <';'>;
    DISCOUNT = <'insert discount'> NAME PERCENT <'when'> CONDITION [<'and'> CONDITION]* <';'>;
    <PERCENT> = NUMBER ;
    PROMOTIONTYPE = STRING ;
    <NAME> = STRING ;
    <DESCRIPTION> = STRING ;
    NUMBER = #'[0-9]+' ;
    <STRING> = #'[A-Za-z][A-Za-z0-9_-]+' ;
    <QUOTED_STRING> = #'[A-Za-z][A-Za-z0-9_-]+' ;
    CONDITION = FACTTYPE<'.'>FIELDS OPERATOR VALUE ;
    FACTTYPE = 'customer' | 'order' | 'order_total' | 'order_line_item' ;
    FIELDS = FIELD[<'.'>FIELD]* ;
    <FIELD> = STRING ;
    OPERATOR = 'is' | '>' | '<' | '=' ;
    <VALUE> = STRING | NUMBER ;
    "
   :auto-whitespace :standard
   :string-ci true))

(def operators {"is" `=
                ">" `< ; reversed to match grammar order
                "<" `> ; reversed to match grammar order
                "=" `=})

(def fact-types
  {"customer" Customer
   "order" Order
   "order_line_item" OrderLineItem
   "order_total" OrderTotal
   "shipping_restriction" ShippingRestriction})

(def shopping-transforms
  {:NUMBER #(Integer/parseInt %)
   :OPERATOR operators
   :FACTTYPE fact-types
   :FIELD keyword
   :FIELDS (fn [& args] args)

   :CONDITION (fn [fact-type fields operator value]
                (let [vec-fields# (vec (map keyword fields))]
                  {:type fact-type
                   :args ['fact]
                   :fact-binding :?fact
                   :constraints [`(~operator ~value (get-in ~'fact ~vec-fields#))]}))

   ;; Convert promotion strings to keywords.
   :PROMOTIONTYPE keyword

   :DISCOUNT (fn [name percent & conditions]
               {:name name
                :lhs conditions
                :rhs `(clara.rules/insert!
                        (shopping/->Discount ~name ~name nil :percent
                                             ~percent))})

   :PROMOTION (fn [name promotion-type & conditions]
                {:name name
                 :lhs conditions
                 :rhs `(clara.rules/insert!
                         (shopping/->Promotion ~name ~name ~'fact :percent ~name
                                               {}))})

   :SHIPPING_RESTRICTION
   ; NOTE: See the example of pulling the ?fact symbol from the lhs.
   (fn [name description & conditions]
     {:name name
      :lhs conditions
      :rhs `(clara.rules/insert! (shopping/->ShippingRestriction (:sku ~'?fact) ~name ~description))})})

;; These rules may be stored in an external file or database.
(def example-rules
  "INSERT shipping_restriction no-explosives text-description-goes-here
     WHEN order_line_item.attributes.isExplosive = kaboom;
  INSERT shipping_restriction noship-expensive-things text-description-goes-here
     WHEN order_line_item.cost > 100;")

(defn load-user-rules
  "Converts a business rule string into Clara productions."
  [business-rules]
  (let [parse-tree ((shopping-grammar) business-rules)]
    (when (insta/failure? parse-tree)
      (throw (ex-info (print-str parse-tree) {:failure parse-tree})))
    (insta/transform shopping-transforms parse-tree)))

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


(defn run-examples
  "Run the example."
  [& {:keys [print-parsed-rules? explain-activations?]}]
  (let [rules (load-user-rules example-rules)]
    (print-parsed-rules rules print-parsed-rules?)
    (-> (clara/mk-session 'ticean.clara.parser
                          'ticean.clara.shopping
                          rules)
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
        (calculate))))


(comment
  ;; Without instrumentation printing.
  (require '[ticean.clara.parser :as parser])
  (clojure.pprint/pprint
    (parser/run-examples :print-parsed-rules? false
                         :explain-activations? false)))
