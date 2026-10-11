-- ЛР2/ЛР3, шаг EXPAND паттерна expand-contract.
--
-- Только добавляет новые структуры: таблицу customers (имя уже разбито на
-- first_name/last_name) и ссылки orders.customer_id / order_items.product_id.
-- Старые колонки orders.customer_* и order_items.product_name/product_price
-- остаются и продолжают заполняться, поэтому работающая версия приложения
-- миграцию не замечает. Удаляются они только contract-миграцией V4.

-- Не ждать блокировку бесконечно: если таблицу держит долгий запрос, лучше
-- упасть и повторить накат, чем выстроить за ALTER очередь из всех запросов.
SET LOCAL lock_timeout = '10s';

CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL DEFAULT '',
    address VARCHAR(500),
    phone VARCHAR(50)
);

CREATE INDEX idx_customers_name ON customers (last_name, first_name);

-- Nullable-колонки без DEFAULT добавляются мгновенно, без перезаписи таблицы.
ALTER TABLE orders ADD COLUMN customer_id BIGINT REFERENCES customers (id);
ALTER TABLE order_items ADD COLUMN product_id BIGINT REFERENCES products (id);

CREATE INDEX idx_orders_customer_id ON orders (customer_id);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_order_items_product_id ON order_items (product_id);
CREATE INDEX idx_reviews_product_id ON reviews (product_id);

-- Новая версия приложения пишет только customer_id/product_id, поэтому до
-- contract-шага старые колонки должны принимать NULL.
ALTER TABLE orders ALTER COLUMN customer_full_name DROP NOT NULL;
ALTER TABLE order_items ALTER COLUMN product_name DROP NOT NULL;
ALTER TABLE order_items ALTER COLUMN product_price DROP NOT NULL;

-- NOT VALID: существующие строки не проверяются (их заполнит backfill в V3),
-- но каждая новая строка обязана иметь ссылку - её проставят триггеры ниже.
ALTER TABLE orders
    ADD CONSTRAINT orders_customer_id_not_null CHECK (customer_id IS NOT NULL) NOT VALID;
ALTER TABLE order_items
    ADD CONSTRAINT order_items_product_id_not_null CHECK (product_id IS NOT NULL) NOT VALID;

-- Разбиение полного имени: первое слово - имя, остаток - фамилия.
-- Та же логика, что в CustomerName.parse на стороне приложения.
CREATE FUNCTION migration_first_name(full_name TEXT) RETURNS TEXT
    LANGUAGE sql IMMUTABLE
    AS $$ SELECT coalesce(substring(full_name FROM '^\s*(\S+)'), '') $$;

CREATE FUNCTION migration_last_name(full_name TEXT) RETURNS TEXT
    LANGUAGE sql IMMUTABLE
    AS $$ SELECT coalesce(substring(full_name FROM '^\s*\S+\s+(.*\S)'), '') $$;

CREATE FUNCTION migration_full_name(first_name TEXT, last_name TEXT) RETURNS TEXT
    LANGUAGE sql IMMUTABLE
    AS $$ SELECT CASE WHEN last_name = '' THEN first_name ELSE first_name || ' ' || last_name END $$;

-- Клиенты с одинаковыми контактными данными схлопываются в одну запись.
CREATE FUNCTION migration_ensure_customer(p_full_name TEXT, p_address TEXT, p_phone TEXT) RETURNS BIGINT
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_first_name TEXT := migration_first_name(p_full_name);
    v_last_name TEXT := migration_last_name(p_full_name);
    v_id BIGINT;
BEGIN
    SELECT id INTO v_id
      FROM customers
     WHERE first_name = v_first_name
       AND last_name = v_last_name
       AND address IS NOT DISTINCT FROM p_address
       AND phone IS NOT DISTINCT FROM p_phone
     ORDER BY id
     LIMIT 1;
    IF v_id IS NULL THEN
        INSERT INTO customers (first_name, last_name, address, phone)
        VALUES (v_first_name, v_last_name, p_address, p_phone)
        RETURNING id INTO v_id;
    END IF;
    RETURN v_id;
END
$$;

-- Позиция связывается с товаром по названию (предпочтительно с той же ценой).
-- Если товара с таким названием больше нет, данные позиции сохраняются как
-- отдельный товар, чтобы backfill не терял историю заказов.
CREATE FUNCTION migration_ensure_product(p_name TEXT, p_price NUMERIC) RETURNS BIGINT
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_id BIGINT;
BEGIN
    SELECT id INTO v_id
      FROM products
     WHERE name = p_name
     ORDER BY (price = p_price) IS TRUE DESC, id
     LIMIT 1;
    IF v_id IS NULL THEN
        INSERT INTO products (name, price) VALUES (p_name, p_price) RETURNING id INTO v_id;
    END IF;
    RETURN v_id;
END
$$;

-- Dual-write на уровне БД, пока работают обе версии приложения:
--  * старая версия пишет только customer_full_name/... -> проставляем customer_id;
--  * новая версия пишет только customer_id -> заполняем старые колонки, чтобы
--    их читатели (старая версия, отчёты) видели те же данные.
CREATE FUNCTION migration_sync_order_customer() RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.customer_id IS NULL AND NEW.customer_full_name IS NOT NULL THEN
        NEW.customer_id := migration_ensure_customer(
                NEW.customer_full_name, NEW.customer_address, NEW.customer_phone);
    ELSIF NEW.customer_id IS NOT NULL AND NEW.customer_full_name IS NULL THEN
        SELECT migration_full_name(c.first_name, c.last_name), c.address, c.phone
          INTO NEW.customer_full_name, NEW.customer_address, NEW.customer_phone
          FROM customers c
         WHERE c.id = NEW.customer_id;
    END IF;
    RETURN NEW;
END
$$;

CREATE FUNCTION migration_sync_order_item_product() RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.product_id IS NULL AND NEW.product_name IS NOT NULL THEN
        NEW.product_id := migration_ensure_product(NEW.product_name, NEW.product_price);
    ELSIF NEW.product_id IS NOT NULL AND NEW.product_name IS NULL THEN
        SELECT p.name, p.price
          INTO NEW.product_name, NEW.product_price
          FROM products p
         WHERE p.id = NEW.product_id;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER orders_sync_customer
    BEFORE INSERT OR UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION migration_sync_order_customer();

CREATE TRIGGER order_items_sync_product
    BEFORE INSERT OR UPDATE ON order_items
    FOR EACH ROW EXECUTE FUNCTION migration_sync_order_item_product();
