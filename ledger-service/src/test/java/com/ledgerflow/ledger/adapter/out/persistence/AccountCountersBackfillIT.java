package com.ledgerflow.ledger.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerflow.ledger.TestcontainersConfiguration;
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
 * Changeset 0003 backfills the posting counters of accounts that already have postings. Runs the real changelog in an
 * isolated schema: the first two changesets, then rows, then the rest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AccountCountersBackfillIT {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";

    @Autowired
    private DataSource dataSource;

    @Test
    void backfillSetsEachAccountsCountersToTheSumsOfItsPostings() throws Exception {
        var schema = "backfill_" + UUID.randomUUID().toString().replace("-", "");
        // A connection of its own, not from the pool: search_path must not leak into other tests.
        var pool = dataSource.unwrap(HikariDataSource.class);
        try (var connection = DriverManager.getConnection(pool.getJdbcUrl(), pool.getUsername(), pool.getPassword())) {
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            jdbc.execute("create schema " + schema);
            try {
                jdbc.execute("set search_path to " + schema);
                migrateWithRowsBeforeChangeset3(jdbc, liquibaseOn(connection, schema));
            } finally {
                jdbc.execute("drop schema " + schema + " cascade");
            }
        }
    }

    private static void migrateWithRowsBeforeChangeset3(JdbcTemplate jdbc, Database database) throws Exception {
        update(database, 2);
        var cash = account(jdbc, "ASSET");
        var customer = account(jdbc, "LIABILITY");
        var unused = account(jdbc, "LIABILITY");
        transfer(jdbc, cash, customer, 10_000);
        transfer(jdbc, customer, cash, 2_500);
        transfer(jdbc, cash, customer, 400);
        update(database, null);

        assertThat(counters(jdbc, cash)).containsExactly(10_400L, 2_500L);
        assertThat(counters(jdbc, customer)).containsExactly(2_500L, 10_400L);
        assertThat(counters(jdbc, unused)).containsExactly(0L, 0L);
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

    private static UUID account(JdbcTemplate jdbc, String type) {
        var id = UUID.randomUUID();
        jdbc.update(
                "insert into accounts (id, currency, type, overdraft_policy, version) values (?, 'EUR', ?, 'ALLOWED', 0)",
                id,
                type);
        return id;
    }

    private static void transfer(JdbcTemplate jdbc, UUID debited, UUID credited, long amount) {
        var transaction = UUID.randomUUID();
        jdbc.update("insert into transactions (id, occurred_at) values (?, now())", transaction);
        posting(jdbc, transaction, debited, amount);
        posting(jdbc, transaction, credited, -amount);
    }

    private static void posting(JdbcTemplate jdbc, UUID transaction, UUID account, long amount) {
        jdbc.update(
                "insert into postings (id, transaction_id, account_id, amount, currency) values (?, ?, ?, ?, 'EUR')",
                UUID.randomUUID(),
                transaction,
                account,
                amount);
    }

    private static Long[] counters(JdbcTemplate jdbc, UUID account) {
        return jdbc.queryForObject(
                "select debits_posted, credits_posted from accounts where id = ?",
                (row, rowNum) -> new Long[] {row.getLong(1), row.getLong(2)},
                account);
    }
}
