(ns ticean.clara
  (:require
    [ticean.clara.shopping :as shopping])
  (:gen-class))

;; These rules may be stored in an external file or database.
(def example-rules
  "INSERT shipping_restriction no-explosives text-description-goes-here
     WHEN order_line_item.attributes.isExplosive = kaboom;
  INSERT shipping_restriction noship-expensive-things text-description-goes-here
     WHEN order_line_item.cost > 100;")

(defn example-cart-facts []
  [{:fact-type 'Customer :status :not-vip}
   {:fact-type 'Order :year 2018 :month :august :day 20 :attributes {}}
   {:fact-type 'OrderLineItem :sku :gizmo :cost 20 :attributes {}}
   {:fact-type 'OrderLineItem :sku :widget :cost 120 :attributes {}}
   {:fact-type 'OrderLineItem :sku :fizzbuzz :cost 90 :attributes {:flammable? true}}
   {:fact-type 'OrderLineItem :sku "firecracker" :cost 10 :attributes {:isExplosive? "kaboom"}}
   {:fact-type 'OrderLineItem :sku "north-face-jacket" :cost 10 :attributes {:brand "NorthFace"}}])

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
