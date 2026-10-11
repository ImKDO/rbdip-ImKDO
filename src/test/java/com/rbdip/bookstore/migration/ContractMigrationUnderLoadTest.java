package com.rbdip.bookstore.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * ЛР3: новая версия приложения уже работает на схеме после expand/backfill,
 * и под HTTP-нагрузкой на API накатывается contract-миграция (удаление
 * старых колонок). Ни один запрос не должен получить не-2xx ответ или таймаут.
 *
 * <p>Эталонный LoadDuringMigrationReferenceTest намеренно не покрывает
 * contract-шаг - этот тест закрывает оставшуюся часть цепочки.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.flyway.enabled=false")
class ContractMigrationUnderLoadTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final MigrationSteps STEPS;
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    static {
        POSTGRES.start();
        STEPS = new MigrationSteps(MigrationSteps.dataSourceOf(POSTGRES));
        STEPS.migrateAllButContract();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void apiAnswersWithoutErrorsWhileContractMigrationRuns() throws Exception {
        long productId = json.readTree(call("POST", "/products", "{\"name\":\"Refactoring\",\"price\":40.00}", 201))
                .get("id").asLong();
        String orderJson = """
                {"customerFullName":"Load Test Customer","customerAddress":"SPb","customerType":"regular",
                 "items":[{"productId":%d,"quantity":1}]}""".formatted(productId);

        ConcurrentLoad load = ConcurrentLoad.start(4, () -> {
            call("POST", "/orders", orderJson, 201);
            call("GET", "/orders", null, 200);
        });
        try (load) {
            load.awaitMoreIterations(20, TIMEOUT);
            STEPS.migrateToLatest();
            load.awaitMoreIterations(20, TIMEOUT);
        }

        assertThat(load.errors())
                .as("API не должно отвечать ошибками/таймаутами во время contract-миграции")
                .isEmpty();
        assertThat(jdbc.queryForList(
                        "SELECT column_name FROM information_schema.columns WHERE table_name = 'orders'",
                        String.class))
                .doesNotContain("customer_full_name", "customer_address", "customer_phone");

        JsonNode orders = json.readTree(call("GET", "/orders", null, 200));
        assertThat(orders).hasSize(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class));
        for (JsonNode order : orders) {
            assertThat(order.get("customerFullName").asText()).isEqualTo("Load Test Customer");
            assertThat(order.get("items").get(0).get("productName").asText()).isEqualTo("Refactoring");
        }
    }

    private String call(String method, String path, String body, int expectedStatus)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json");
        request = body == null ? request.GET() : request.method(method, HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != expectedStatus) {
            throw new IllegalStateException(method + " " + path + " -> " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }
}
