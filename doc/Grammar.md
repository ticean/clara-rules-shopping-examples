Example queries that can be parsed into the rules engine.

```sql
/* Restrict alchohol shipments to illegal states. */
INSERT shipping_restriction
WHERE order_line_item.attributes.alcohol? IS true
  AND order.shipping_address.state IN ["AL" "OK" "UT"];
```

```sql
/*
  Restrict an item, brand, or category by State, by Country
  (i.e Knives from New York State, The North Face from Canada)
*/

-- Restrict knives from New York State.
INSERT shipping_restriction
WHERE order_line_item.attributes.tags INTERSECT ["knives" "guns n ammo"]
  AND order_shipping_address.state IN ["NY"];


-- Restrict North Face from Canada.
INSERT shipping_restriction
WHERE order_line_items.attributes.brand = "Canada";
```
