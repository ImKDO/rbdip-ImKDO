package com.rbdip.bookstore.migration;

import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Пошаговый накат миграций из src/main/resources/db/migration. Как и в
 * эталонном LoadDuringMigrationReferenceTest, последняя миграция цепочки
 * считается contract-шагом, всё до неё - expand/backfill.
 */
final class MigrationSteps {

    static final String INITIAL_VERSION = "1";

    private final DataSource dataSource;
    private final List<String> versions;

    MigrationSteps(DataSource dataSource) {
        this.dataSource = dataSource;
        this.versions = Arrays.stream(configure().load().info().all())
                .map(info -> info.getVersion().getVersion())
                .toList();
    }

    static PGSimpleDataSource dataSourceOf(PostgreSQLContainer<?> postgres) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        return dataSource;
    }

    String lastBeforeContract() {
        return versions.get(versions.size() - 2);
    }

    void migrateToInitial() {
        migrateTo(INITIAL_VERSION);
    }

    void migrateAllButContract() {
        migrateTo(lastBeforeContract());
    }

    void migrateToLatest() {
        configure().load().migrate();
    }

    private void migrateTo(String version) {
        configure().target(version).load().migrate();
    }

    private FluentConfiguration configure() {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
    }
}
