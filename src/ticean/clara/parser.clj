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
    [ticean.clara.shopping Customer Discount Order OrderLineItem OrderPromoCode
                           OrderTotal PromotionShippingRestriction]))

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

(def unused-rules
  "insert discount my-discount 15
     when customer.status is platinum;

   insert discount extra-discount 10
     when customer.status is gold and order_total.value > 200;

   insert promotion free-widget-month free-widget
     when customer.status is gold
      and order.month is august;")



(defn load-user-rules
  "Converts a business rule string into Clara productions."
  [business-rules]
  (let [parse-tree ((shopping-grammar) business-rules)]
    (when (insta/failure? parse-tree)
      (throw (ex-info (print-str parse-tree) {:failure parse-tree})))
    (insta/transform shopping-transforms parse-tree)))

(defn print-parsed-rules [rules]
  (println "PARSED RULES: \n")
  (clojure.pprint/pprint rules)
  (println "\n\n"))

(defn run-examples
  "Run the example."
  []
  (let [rules (load-user-rules example-rules)]
    (print-parsed-rules rules)
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
        ;#(when explain-activations? (inspect/explain-activations %))
        (shopping/print-discounts!)
        (shopping/print-promotions!)
        (shopping/print-shipping-restrictions!))))
