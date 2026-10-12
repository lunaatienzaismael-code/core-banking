package com.ledgerflow.ledger;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/** UC1 query movements, end to end over HTTP against a real PostgreSQL, with postings inserted by {@link LedgerFixture}. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AccountMovementsIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private LedgerFixture ledger;
    private RestTestClient client;

    @BeforeEach
    void setUp() {
        ledger = new LedgerFixture(jdbc);
        client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    @DisplayName("AC1.3 movements are the account's posting lines in chronological order")
    void movementsAreTheAccountsPostingLinesInChronologicalOrder() {
        var customer = ledger.account("EUR", "LIABILITY");
        var cash = ledger.account("EUR", "ASSET");
        var withdrawal = ledger.transfer(customer, cash, 2_500, "EUR", at(16, 14));
        var deposit = ledger.transfer(cash, customer, 10_000, "EUR", at(15, 9));

        client.get()
                .uri("/accounts/{id}/movements", customer)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.accountId")
                .isEqualTo(customer.toString())
                .jsonPath("$.movements.length()")
                .isEqualTo(2)
                .jsonPath("$.movements[0].transactionId")
                .isEqualTo(deposit.toString())
                .jsonPath("$.movements[0].amount.amount")
                .isEqualTo(10_000)
                .jsonPath("$.movements[0].amount.currency")
                .isEqualTo("EUR")
                .jsonPath("$.movements[0].direction")
                .isEqualTo("CREDIT")
                .jsonPath("$.movements[0].occurredAt")
                .isEqualTo("2026-01-15T09:00:00Z")
                .jsonPath("$.movements[0].postingId")
                .isNotEmpty()
                .jsonPath("$.movements[1].transactionId")
                .isEqualTo(withdrawal.toString())
                .jsonPath("$.movements[1].amount.amount")
                .isEqualTo(2_500)
                .jsonPath("$.movements[1].direction")
                .isEqualTo("DEBIT")
                .jsonPath("$.movements[1].occurredAt")
                .isEqualTo("2026-01-16T14:00:00Z");
    }

    @Test
    @DisplayName("B1 movements of an unknown account are a Problem Details 404")
    void unknownAccountIsProblemDetails404() {
        var unknown = UUID.randomUUID();

        client.get()
                .uri("/accounts/{id}/movements", unknown)
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(404)
                .jsonPath("$.instance")
                .isEqualTo("/accounts/" + unknown + "/movements");
    }

    @Test
    @DisplayName("B2 a newly opened account has an empty movement list (AC1.1)")
    void newlyOpenedAccountHasNoMovements() {
        var location = client.post()
                .uri("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"currency\": \"EUR\", \"type\": \"LIABILITY\", \"overdraftPolicy\": \"NOT_ALLOWED\"}")
                .exchange()
                .expectStatus()
                .isCreated()
                .returnResult()
                .getResponseHeaders()
                .getLocation();

        client.get()
                .uri(location.getPath() + "/movements")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.movements")
                .isEmpty();
    }

    private static OffsetDateTime at(int day, int hour) {
        return OffsetDateTime.of(2026, 1, day, hour, 0, 0, 0, ZoneOffset.UTC);
    }
}
