package com.ledgerflow.messaging.outbox;

import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** PostgreSQL with the outbox table from the same DDL the services apply, started once per JVM. */
final class OutboxDatabase {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");
    static final DataSource DATA_SOURCE;
    static final JdbcClient JDBC;
    static final TransactionTemplate TRANSACTIONS;

    static {
        POSTGRES.start();
        var dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        new ResourceDatabasePopulator(new ClassPathResource("db/messaging/create-outbox-messages.sql"))
                .execute(dataSource);
        DATA_SOURCE = dataSource;
        JDBC = JdbcClient.create(dataSource);
        TRANSACTIONS = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    private OutboxDatabase() {}
}
