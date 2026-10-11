# Миграции

`V1__init.sql` — начальная схема, эталон, не редактируется.

Свои миграции добавляйте следующими номерами: `V2__...sql`, `V3__...sql` и т.д.
(стандартная схема именования Flyway: `V<версия>__<описание>.sql`).

## Цепочка нормализации (ЛР2 + ЛР3, паттерн expand-contract)

| Миграция | Шаг | Что делает | Блокировки |
|---|---|---|---|
| `V2__expand_customers_and_product_links` | expand | `customers` (имя уже разбито на `first_name`/`last_name`), `orders.customer_id`, `order_items.product_id`, индексы, `CHECK ... NOT VALID`, триггеры двусторонней синхронизации | короткие `ACCESS EXCLUSIVE` на `ALTER TABLE`, `lock_timeout = 10s` |
| `V3__backfill_customers_and_product_links` | migrate | перенос существующих строк, `VALIDATE CONSTRAINT` | только построчные + `SHARE UPDATE EXCLUSIVE` |
| `V4__contract_drop_denormalized_columns` | contract | удаление триггеров, старых колонок, `SET NOT NULL` по провалидированной проверке | короткие, без сканирования таблиц |

Пока не накатан contract, обе версии приложения работают одновременно:
триггеры из V2 проставляют `customer_id`/`product_id` для записей старой
версии и заполняют старые колонки для записей новой (dual-write на уровне БД).

Порядок выката без остановки приложения:

1. Накатить V2 и V3 — работающая старая версия их не замечает
   (проверяют `LoadDuringMigrationReferenceTest` и `ExpandMigrationCompatibilityTest`).
2. Выкатить новую версию приложения (читает и пишет `customers`/`products`),
   постепенно заменив все экземпляры старой.
3. Накатить V4 — новая версия продолжает обслуживать запросы
   (проверяет `ContractMigrationUnderLoadTest`).

Contract-миграция всегда последняя в цепочке: эталонный тест ЛР3 накатывает
под нагрузкой всё, кроме последней миграции. Новые expand-шаги добавляются
перед следующим contract-шагом.
