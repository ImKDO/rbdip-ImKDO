package com.rbdip.bookstore.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.customer.CustomerName;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * ЛР3: expand- и backfill-миграции не ломают старую версию приложения,
 * которая продолжает писать и читать денормализованные колонки, а данные
 * обеих версий сходятся в нормализованной схеме.
 */
@Testcontainers
class ExpandMigrationCompatibilityTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final int LEGACY_ORDERS = 2_000;
    private static final int LEGACY_CUSTOMERS = 100;
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private MigrationSteps steps;

    @BeforeEach
    void startFromLegacySchemaWithData() {
        dataSource = MigrationSteps.dataSourceOf(POSTGRES);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        steps = new MigrationSteps(dataSource);
        steps.migrateToInitial();
        seedLegacyData();
    }

    @Test
    void legacyApplicationKeepsWritingAndReadingWhileExpandAndBackfillRun() throws Exception {
        ConcurrentLoad load = ConcurrentLoad.start(4, this::placeAndReadLegacyOrder);
        try (load) {
            load.awaitMoreIterations(20, TIMEOUT);
            steps.migrateAllButContract();
            load.awaitMoreIterations(20, TIMEOUT);
        }

        assertThat(load.errors())
                .as("старая версия приложения не должна получать ошибки во время expand/backfill")
                .isEmpty();
        assertThat(count("SELECT count(*) FROM orders WHERE customer_id IS NULL"))
                .as("все заказы, включая созданные под нагрузкой, связаны с клиентом")
                .isZero();
        assertThat(count("SELECT count(*) FROM order_items WHERE product_id IS NULL"))
                .as("все позиции, включая созданные под нагрузкой, связаны с товаром")
                .isZero();
    }

    @Test
    void backfillSplitsNamesExactlyLikeTheApplicationAndDeduplicatesCustomers() {
        steps.migrateAllButContract();

        List<String[]> rows = jdbc.query(
                "SELECT o.customer_full_name, c.first_name, c.last_name"
                        + " FROM orders o JOIN customers c ON c.id = o.customer_id",
                (rs, i) -> new String[] {rs.getString(1), rs.getString(2), rs.getString(3)});
        assertThat(rows).hasSize(LEGACY_ORDERS + 4);
        for (String[] row : rows) {
            assertThat(new CustomerName(row[1], row[2])).isEqualTo(CustomerName.parse(row[0]));
        }

        assertThat(nameOfCustomerWhoOrdered("  Anna Maria   Sidorova ")).isEqualTo("Anna|Maria   Sidorova");
        assertThat(nameOfCustomerWhoOrdered("Madonna")).isEqualTo("Madonna|");
        assertThat(count("SELECT count(DISTINCT customer_id) FROM orders WHERE customer_full_name = 'Ivan Petrov'"))
                .isOne();
        assertThat(count("SELECT count(*) FROM customers")).isEqualTo(LEGACY_CUSTOMERS + 3);
    }

    @Test
    void backfillLinksItemsToProductsByName() {
        steps.migrateAllButContract();

        assertThat(linkedProducts("Refactoring", "40.00"))
                .as("цена товара изменилась после заказа - позиция связывается с тем же товаром")
                .containsExactly("Refactoring|45.00");
        assertThat(linkedProducts("Legacy Book", "10.00"))
                .as("товара больше нет в каталоге - данные позиции сохраняются отдельным товаром")
                .containsExactly("Legacy Book|10.00");
    }

    @Test
    void bothApplicationVersionsSeeTheSameDataUntilContract() {
        steps.migrateAllButContract();
        Long customerId = jdbc.queryForObject(
                "SELECT customer_id FROM orders WHERE customer_full_name = 'Madonna'", Long.class);
        Long productId = jdbc.queryForObject("SELECT id FROM products WHERE name = 'Clean Code'", Long.class);

        Long newStyleOrder = jdbc.queryForObject(
                "INSERT INTO orders (customer_id, status, created_at) VALUES (?, 'new', now()) RETURNING id",
                Long.class, customerId);
        jdbc.update("INSERT INTO order_items (order_id, product_id, quantity) VALUES (?, ?, 1)",
                newStyleOrder, productId);

        assertThat(jdbc.queryForObject(
                        "SELECT o.customer_full_name || '|' || o.customer_address || '|' || i.product_name"
                                + " || '|' || i.product_price FROM orders o JOIN order_items i ON i.order_id = o.id"
                                + " WHERE o.id = ?",
                        String.class, newStyleOrder))
                .as("запись новой версии видна читателям старых колонок")
                .isEqualTo("Madonna|LA|Clean Code|35.00");
    }

    @Test
    void contractKeepsAllMigratedData() {
        steps.migrateToLatest();

        assertThat(columnsOf("orders")).doesNotContain("customer_full_name", "customer_address", "customer_phone");
        assertThat(columnsOf("order_items")).doesNotContain("product_name", "product_price");
        assertThat(jdbc.queryForList(
                        "SELECT c.first_name || '|' || c.last_name || '|' || c.address || '|' || p.name"
                                + " FROM orders o JOIN customers c ON c.id = o.customer_id"
                                + " JOIN order_items i ON i.order_id = o.id JOIN products p ON p.id = i.product_id"
                                + " WHERE c.phone = '+79990000000' ORDER BY o.id",
                        String.class))
                .containsExactly("Ivan|Petrov|Moscow|Clean Code", "Ivan|Petrov|Moscow|Refactoring");
        assertThat(count("SELECT count(*) FROM order_items")).isEqualTo(LEGACY_ORDERS + 3);
    }

    private void seedLegacyData() {
        jdbc.update("INSERT INTO products (name, price) VALUES ('Clean Code', 35.00), ('Refactoring', 45.00)");
        jdbc.update("INSERT INTO orders (customer_full_name, customer_address, customer_phone) VALUES"
                + " ('Ivan Petrov', 'Moscow', '+79990000000'),"
                + " ('Ivan Petrov', 'Moscow', '+79990000000'),"
                + " ('  Anna Maria   Sidorova ', 'SPb', NULL),"
                + " ('Madonna', 'LA', NULL)");
        jdbc.update("INSERT INTO order_items (order_id, product_name, product_price, quantity) VALUES"
                + " (1, 'Clean Code', 35.00, 1),"
                + " (2, 'Refactoring', 40.00, 2),"
                + " (3, 'Legacy Book', 10.00, 1)");
        jdbc.update("INSERT INTO orders (customer_full_name, customer_address)"
                + " SELECT 'Customer ' || (g % ?), 'Street ' || (g % ?) FROM generate_series(1, ?) g",
                LEGACY_CUSTOMERS, LEGACY_CUSTOMERS, LEGACY_ORDERS);
        jdbc.update("INSERT INTO order_items (order_id, product_name, product_price, quantity)"
                + " SELECT id, 'Clean Code', 35.00, 1 FROM orders WHERE customer_full_name LIKE 'Customer %'");
    }

    /** Сценарий старой версии приложения: создать заказ через старые колонки и прочитать его. */
    private void placeAndReadLegacyOrder() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            long orderId;
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO orders (customer_full_name, customer_address, customer_phone, status, created_at)"
                            + " VALUES ('Legacy Customer', 'Legacy Street', NULL, 'new', now()) RETURNING id");
                    ResultSet keys = insert.executeQuery()) {
                keys.next();
                orderId = keys.getLong(1);
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO order_items (order_id, product_name, product_price, quantity)"
                            + " VALUES (?, 'Clean Code', 35.00, 1)")) {
                insert.setLong(1, orderId);
                insert.executeUpdate();
            }
            connection.commit();
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT o.customer_full_name, i.product_name"
                            + " FROM orders o JOIN order_items i ON i.order_id = o.id WHERE o.id = ?")) {
                select.setLong(1, orderId);
                try (ResultSet rs = select.executeQuery()) {
                    if (!rs.next() || !"Legacy Customer".equals(rs.getString(1))) {
                        throw new IllegalStateException("legacy read returned unexpected data for order " + orderId);
                    }
                }
            }
            connection.commit();
        }
    }

    private String nameOfCustomerWhoOrdered(String legacyFullName) {
        return jdbc.queryForObject(
                "SELECT DISTINCT c.first_name || '|' || c.last_name FROM orders o"
                        + " JOIN customers c ON c.id = o.customer_id WHERE o.customer_full_name = ?",
                String.class, legacyFullName);
    }

    private List<String> linkedProducts(String legacyName, String legacyPrice) {
        return jdbc.queryForList(
                "SELECT p.name || '|' || p.price FROM order_items i JOIN products p ON p.id = i.product_id"
                        + " WHERE i.product_name = ? AND i.product_price = ?::numeric",
                String.class, legacyName, legacyPrice);
    }

    private List<String> columnsOf(String table) {
        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?",
                String.class, table);
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
