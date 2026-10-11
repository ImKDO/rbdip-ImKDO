-- ЛР2/ЛР3, шаг MIGRATE (backfill) паттерна expand-contract.
--
-- Переносит в новую структуру строки, созданные до V2; всё, что вставлено
-- после V2, уже заполнено триггерами. UPDATE берёт только построчные
-- блокировки, так что чтение и запись в orders/order_items продолжаются.
-- (На больших таблицах backfill стоит дробить на пачки по id.)

UPDATE orders
   SET customer_id = migration_ensure_customer(customer_full_name, customer_address, customer_phone)
 WHERE customer_id IS NULL;

UPDATE order_items
   SET product_id = migration_ensure_product(product_name, product_price)
 WHERE product_id IS NULL;

-- VALIDATE берёт лишь SHARE UPDATE EXCLUSIVE (не мешает чтению и записи).
-- Валидная CHECK-проверка позволяет contract-шагу сделать SET NOT NULL без
-- полного сканирования таблицы под эксклюзивной блокировкой.
ALTER TABLE orders VALIDATE CONSTRAINT orders_customer_id_not_null;
ALTER TABLE order_items VALIDATE CONSTRAINT order_items_product_id_not_null;
