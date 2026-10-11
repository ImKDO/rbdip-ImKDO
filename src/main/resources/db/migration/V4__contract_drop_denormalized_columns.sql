-- ЛР2/ЛР3, шаг CONTRACT паттерна expand-contract. Всегда последний в цепочке.
--
-- Накатывается только когда все экземпляры приложения уже работают с новой
-- моделью (читают и пишут customers/products) и старые колонки никто не
-- читает. Миграция короткая: таблицы не переписываются и не сканируются.

SET LOCAL lock_timeout = '10s';

DROP TRIGGER orders_sync_customer ON orders;
DROP TRIGGER order_items_sync_product ON order_items;
DROP FUNCTION migration_sync_order_customer();
DROP FUNCTION migration_sync_order_item_product();
DROP FUNCTION migration_ensure_customer(TEXT, TEXT, TEXT);
DROP FUNCTION migration_ensure_product(TEXT, NUMERIC);
DROP FUNCTION migration_full_name(TEXT, TEXT);
DROP FUNCTION migration_first_name(TEXT);
DROP FUNCTION migration_last_name(TEXT);

-- Провалидированная в V3 CHECK-проверка доказывает отсутствие NULL, поэтому
-- SET NOT NULL обходится без полного сканирования (PostgreSQL 12+).
ALTER TABLE orders ALTER COLUMN customer_id SET NOT NULL;
ALTER TABLE orders DROP CONSTRAINT orders_customer_id_not_null;
ALTER TABLE order_items ALTER COLUMN product_id SET NOT NULL;
ALTER TABLE order_items DROP CONSTRAINT order_items_product_id_not_null;

ALTER TABLE orders
    DROP COLUMN customer_full_name,
    DROP COLUMN customer_address,
    DROP COLUMN customer_phone;

ALTER TABLE order_items
    DROP COLUMN product_name,
    DROP COLUMN product_price;
