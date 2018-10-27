(ns ticean.clara.parser
  "Rule grammar built with Instaparse.

  This is extended from the Clara examples.
  https://github.com/cerner/clara-examples
  "
  (:require
    [instaparse.core :as insta]
    [ticean.clara.shopping :as shopping])
  (:import
    [ticean.clara.shopping ActiveShippingMethod Customer Order OrderPromoCode
       OrderLineItem OrderLineItemSubtotal Discount Promotion ShippingAddress
       ShippingMethod ShippingRestriction]))

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
    FACTTYPE = 'customer' | 'order' | 'order_total' | 'order_line_item' | 'shipping_address' ;
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
   "order_line_item_subtotal" OrderLineItemSubtotal
   "shipping_address" ShippingAddress
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

(defn load-user-rules
  "Converts a business rule string into Clara productions."
  [rule-string]
  (let [parse-tree ((shopping-grammar) rule-string)]
    (when (insta/failure? parse-tree)
      (throw (ex-info (print-str parse-tree) {:failure parse-tree})))
    (insta/transform shopping-transforms parse-tree)))
