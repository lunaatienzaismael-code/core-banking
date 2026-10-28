package com.ledgerflow.payment.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerflow.payment.TestcontainersConfiguration;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.command.CommandScope;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Change set 0002 gives payments created before it a key no client was given and a hash no request matches. Runs the
 * real changelog in an isolated schema: the first change set, then rows, then the rest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PaymentIdempotencyBackfillIT {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";

    @Autowired
    private DataSource dataSource;

    @Test
    void existingPaymentsGetALegacyKeyAndAHashNoRequestMatches() throws Exception {
        var schema = "backfill_" + UUID.randomUUID().toString().replace("-", "");
        // A connection of its own, not from the pool: search_path must not leak into other tests.
        var pool = dataSource.unwrap(HikariDataSource.class);
        try (var connection = DriverManager.getConnection(pool.getJdbcUrl(), pool.getUsername(), pool.getPassword())) {
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            jdbc.execute("create schema " + schema);
            try {
                jdbc.execute("set search_path to " + schema);
                migrateWithRowsBeforeChangeset2(jdbc, liquibaseOn(connection, schema));
            } finally {
                jdbc.execute("drop schema " + schema + " cascade");
            }
        }
    }

    private static void migrateWithRowsBeforeChangeset2(JdbcTemplate jdbc, Database database) throws Exception {
        update(database, 1);
        var first = payment(jdbc);
        var second = payment(jdbc);
        update(database, null);

        assertThat(keyAndHash(jdbc, first)).containsExactly("legacy-" + first, "0".repeat(64));
        assertThat(keyAndHash(jdbc, second)).containsExactly("legacy-" + second, "0".repeat(64));
    }

    private static Database liquibaseOn(Connection connection, String schema) throws Exception {
        var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
        database.setDefaultSchemaName(schema);
        database.setLiquibaseSchemaName(schema);
        return database;
    }

    private static void update(Database database, Integer count) throws Exception {
        var command = count == null ? new CommandScope("update") : new CommandScope("updateCount");
        command.addArgumentValue("database", database).addArgumentValue("changelogFile", CHANGELOG);
        if (count != null) {
            command.addArgumentValue("count", count);
        }
        command.execute();
    }

    private static UUID payment(JdbcTemplate jdbc) {
        var id = UUID.randomUUID();
        jdbc.update(
                "insert into payments (id, debtor_account_id, creditor_account_id, amount, currency, status,"
                        + " initiated_at, version) values (?, ?, ?, 2500, 'EUR', 'INITIATED', now(), 0)",
                id,
                UUID.randomUUID(),
                UUID.randomUUID());
        return id;
    }

    private static String[] keyAndHash(JdbcTemplate jdbc, UUID payment) {
        return jdbc.queryForObject(
                "select idempotency_key, request_hash from payments where id = ?",
                (row, rowNum) -> new String[] {row.getString(1), row.getString(2)},
                payment);
    }
}
