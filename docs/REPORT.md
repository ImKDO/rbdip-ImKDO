# Отчёт по лабораторным работам

Локальный прогон пайплайна из `.github/workflows/ci.yml`
(`mvn verify` → PIT → `scripts/write_score.py`): **100/100**.

| Гейт | Результат |
|---|---|
| `lab1_style` | Checkstyle 0, PMD 0 нарушений |
| `lab2_coverage` | покрытие строк 95.9% (порог 60%) |
| `lab2_schema` | `SchemaNormalizationReferenceTest` зелёный |
| `lab3_migration` | `LoadDuringMigrationReferenceTest` зелёный |
| `lab4_nplusone` | `GET /orders` — 1 SQL-запрос (порог 3) |
| `lab4_architecture` | `ArchitectureRulesTest` зелёный |
| `lab5_mutation` | mutation score 100% (68/68) |
| `lab5_reference` | все эталонные тесты зелёные |

История коммитов повторяет порядок работ: инфраструктура → характеризационные
тесты → ЛР1 → expand → contract → ЛР4 → ЛР5.

## Проблемы шаблона, мешавшие проверке

- **Testcontainers 1.20.1** обращается к Docker по API 1.32, который Docker 29+
  отвергает (`client version 1.32 is too old`), — падали все интеграционные
  тесты. Версия поднята до 1.21.4 (последняя 1.x, пакеты классов те же).
- **PIT без `pitest-junit5-plugin`** не видит JUnit 5 тесты, и mutation score
  всегда 0. Плагин добавлен в `pom.xml`.
- `scripts/write_score.py` ищет в `target/pmd.xml` элементы `violation` без
  пространства имён, а PMD 7 пишет их в `http://pmd.sourceforge.net/report/2.0.0`,
  поэтому скрипт всегда считает 0 нарушений PMD. Скрипт не менялся; сами
  нарушения PMD устранены, в отчёте их действительно 0.

## ЛР1 — найденные проблемы и рефакторинг

Code smells:

1. **God-метод** `OrderService.createOrder`: валидация, загрузка товаров, расчёт
   цены, сохранение и «отправка письма» в одном методе (цикломатическая
   сложность 16, NPath 540).
2. **Magic numbers и цепочки if по строкам** в `PricingCalculator`
   (10, 0.95, 0.9, 0.85, 0.8, 1000, 0.98; `"vip"`, `"SAVE10"`...).
3. **Побочный эффект внутри транзакции**: письмо «уходило» до коммита, то есть
   и при откате; вывод через `System.out`.
4. **Контроллер работает с репозиториями напрямую**, `GET /orders` с N+1.
5. **Запрос к БД на каждую позицию** при создании заказа (`findById` в цикле).
6. **`Map<String, Object>` вместо DTO** в контроллерах, приведения типов из JSON.
7. `ReviewService`: **неиспользуемая переменная** с бессмысленной проверкой
   («есть ли вообще заказы») и прямая связанность с репозиториями `order`.
8. `PricingCalculator` создаётся через `new` внутри сервиса, а не внедряется.

DB smells:

1. `orders` хранит контактные данные клиента в каждом заказе — нет таблицы `customers`.
2. `order_items` копирует название и цену товара; ссылки на `products` нет вовсе.
3. Полное имя в одной колонке `customer_full_name`.
4. Нет индексов на внешних ключах (`order_items.order_id`, `reviews.product_id`).

Что сделано:

- `OrderService` только координирует сценарий; валидация — `OrderRequestValidator`;
  уведомление — событие `OrderPlacedEvent`, которое `OrderConfirmationSender`
  обрабатывает после коммита (`@TransactionalEventListener`), лог через SLF4J.
- `PricingCalculator`: именованные константы; тип клиента и купоны — enum'ы
  `CustomerType` и `Coupon` (замена условий полиморфизмом); обнуление итога
  через `max(ZERO)`. Поведение зафиксировано характеризационными тестами
  до рефакторинга.
- Товары заказа загружаются одним `findAllById`.

## ЛР2 — характеризационные тесты и нормализация

- `PricingCalculatorCharacterizationTest` (47 проверок): строгие границы
  (> 10 шт., > 1000), регистр типов и купонов, порядок скидок (скидка клиента
  до купона, скидка за крупный заказ после всех), обнуление отрицательного
  итога, округление HALF_UP, нулевые и отрицательные количества и цены, `null`.
- Схема: таблица `customers`, `orders.customer_id → customers`,
  `order_items.product_id → products`; дублирующиеся колонки удалены.
  Миграции `V2`–`V4` описаны в
  [README миграций](../src/main/resources/db/migration/README.md).

## ЛР3 — expand-contract без даунтайма

- `V2` (expand) добавляет новые структуры, `CHECK ... NOT VALID` и триггеры
  двусторонней синхронизации (dual-write на уровне БД): старая версия
  приложения пишет `customer_full_name` → проставляется `customer_id`, новая
  пишет `customer_id` → заполняются старые колонки для старых читателей.
- `V3` (backfill) переносит данные и валидирует ограничения без эксклюзивных
  блокировок. `V4` (contract, всегда последняя) удаляет старые колонки.
- Свои нагрузочные тесты (`src/test/java/.../migration/`):
  - `ExpandMigrationCompatibilityTest` — старая версия приложения пишет и читает
    под нагрузкой во время V2–V3 без ошибок; данные переносятся корректно,
    имена разбиваются так же, как `CustomerName.parse`;
  - `ContractMigrationUnderLoadTest` — новая версия приложения под HTTP-нагрузкой
    (`POST`/`GET /orders`) переживает contract-шаг без не-2xx ответов и таймаутов.
  - Оба теста проверены на заведомо сломанных вариантах: без триггера и с
    маппингом удаляемой колонки они падают.

## ЛР4 — N+1 и выделение модуля review

- `OrderRepository.findAllWithCustomerAndItems()` — один запрос с `JOIN FETCH`
  вместо 1 + N; чтение вынесено в `OrderQueryService`.
- Strangler Fig: `review` объявляет свой контракт `PurchaseVerifier`, реализует
  его `order.OrderPurchaseVerifier`. Зависимость `review → order` исчезла
  (`order → review` допустима). Проверка покупки стала осмысленной: автор отзыва
  заказывал этот товар; результат возвращается как `verifiedPurchase`.

## ЛР5 — mutation testing

PIT мутирует ключевые модули с логикой (расчёт цены, оформление и чтение
заказов, клиенты, отзывы); сущности, контроллеры и эмулятор почты в набор
не входят. Мутации ловят быстрые unit-тесты; интеграционные тесты исключены из
прогона PIT, потому что мутантов не добавляют, а требуют Docker.
Результат: 68 мутантов, убиты все 68.

## Запуск

```bash
mvn verify                                          # тесты + Checkstyle + PMD + JaCoCo
mvn org.pitest:pitest-maven:mutationCoverage        # PIT
python3 scripts/write_score.py                      # score.json
```

Нужен JDK 21 (JaCoCo 0.8.12 не поддерживает более новые версии байткода)
и запущенный Docker.
