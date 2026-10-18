package com.ledgerflow.ledger;

import static org.assertj.core.api.Assertions.assertThat;

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

/** UC2 AC2.3 no overdraft on constrained accounts, end to end over HTTP against a real PostgreSQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RejectOverdraftIT {

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
    @DisplayName("AC2.3 a withdrawal leaving a constrained customer account at exactly zero is accepted")
    void withdrawalDownToExactlyZeroIsAccepted() {
        var cash = ledger.account("EUR", "ASSET");
        var customer = deposit(cash, 1_000);

        post(line(customer, 1_000, "DEBIT"), line(cash, 1_000, "CREDIT"))
                .expectStatus()
                .isCreated();

        expectBalance(customer, 0);
    }

    @Test
    @DisplayName("AC2.3 a withdrawal one minor unit beyond a constrained customer's balance is a Problem Details 422"
            + " and nothing is persisted")
    void withdrawalOneMinorUnitShortIsRejected() {
        var cash = ledger.account("EUR", "ASSET");
        var customer = deposit(cash, 1_000);
        var before = snapshot(cash, customer);

        post(line(customer, 1_001, "DEBIT"), line(cash, 1_001, "CREDIT"))
                .expectStatus()
                .isEqualTo(422)
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.title")
                .isEqualTo("Insufficient funds")
                .jsonPath("$.detail")
                .isEqualTo("Account " + customer + " has insufficient funds for a posting of 1001 EUR");

        assertThat(snapshot(cash, customer)).isEqualTo(before);
        expectBalance(customer, 1_000);
    }

    @Test
    @DisplayName("AC2.3 a debit-normal constrained account is held to zero on its debit side")
    void debitNormalConstrainedAccountIsHeldToZero() {
        var nostro = ledger.account("EUR", "ASSET", "NOT_ALLOWED");
        var clearing = ledger.account("EUR", "LIABILITY");
        ledger.transfer(nostro, clearing, 500, "EUR");
        var before = snapshot(nostro, clearing);

        post(line(clearing, 501, "DEBIT"), line(nostro, 501, "CREDIT"))
                .expectStatus()
                .isEqualTo(422);
        assertThat(snapshot(nostro, clearing)).isEqualTo(before);

        post(line(clearing, 500, "DEBIT"), line(nostro, 500, "CREDIT"))
                .expectStatus()
                .isCreated();
        expectBalance(nostro, 0);
    }

    @Test
    @DisplayName("AC2.3 an account whose policy allows it may go below zero")
    void allowedAccountMayGoBelowZero() {
        var cash = ledger.account("EUR", "ASSET");
        var suspense = ledger.account("EUR", "LIABILITY", "ALLOWED");

        post(line(suspense, 7_500, "DEBIT"), line(cash, 7_500, "CREDIT"))
                .expectStatus()
                .isCreated();

        expectBalance(suspense, -7_500);
    }

    @Test
    @DisplayName("AC2.3 when a later line is refused, the earlier lines of the same transaction are rolled back too")
    void refusedLineRollsBackTheWholeTransaction() {
        var cash = ledger.account("EUR", "ASSET");
        var alice = deposit(cash, 300);
        var bob = deposit(cash, 300);
        // Counters are applied in ascending account id order (ADR 0009): the first customer's line fits and is applied,
        // then the second one's is refused, so the rollback must undo a counter that was already written.
        var first = alice.compareTo(bob) < 0 ? alice : bob;
        var second = first == alice ? bob : alice;
        var before = snapshot(cash, first, second);

        post(line(first, 200, "DEBIT"), line(second, 400, "DEBIT"), line(cash, 600, "CREDIT"))
                .expectStatus()
                .isEqualTo(422)
                .expectBody()
                .jsonPath("$.detail")
                .isEqualTo("Account " + second + " has insufficient funds for a posting of 400 EUR");

        assertThat(snapshot(cash, first, second)).isEqualTo(before);
    }

    /** A constrained customer deposit account (credit-normal) holding {@code amount}. */
    private UUID deposit(UUID cash, long amount) {
        var customer = ledger.account("EUR", "LIABILITY", "NOT_ALLOWED");
        ledger.transfer(cash, customer, amount, "EUR");
        return customer;
    }

    private RestTestClient.ResponseSpec post(String... lines) {
        return client.post()
                .uri("/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"lines\": [" + String.join(", ", lines) + "]}")
                .exchange();
    }

    private static String line(UUID account, long amount, String direction) {
        return "{\"accountId\": \"%s\", \"amount\": {\"amount\": %d, \"currency\": \"EUR\"}, \"direction\": \"%s\"}"
                .formatted(account, amount, direction);
    }

    /** Transaction rows, plus postings and counters of the given accounts: equal snapshots mean nothing was written. */
    private String snapshot(UUID... accounts) {
        var state = new StringBuilder().append(jdbc.queryForObject("select count(*) from transactions", Long.class));
        for (var account : accounts) {
            state.append(jdbc.queryForObject(
                    "select '|' || (select count(*) from postings where account_id = a.id)"
                            + " || ',' || debits_posted || ',' || credits_posted from accounts a where id = ?",
                    String.class,
                    account));
        }
        return state.toString();
    }

    private void expectBalance(UUID account, long amount) {
        client.get()
                .uri("/accounts/{id}/balance", account)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.balance.amount")
                .isEqualTo(amount);
    }
}
