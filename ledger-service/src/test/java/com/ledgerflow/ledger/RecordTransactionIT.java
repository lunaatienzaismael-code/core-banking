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

/** UC2 record a transaction, end to end over HTTP against a real PostgreSQL. All data is fictitious. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RecordTransactionIT {

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
    @DisplayName("AC2.1 a balanced transaction is accepted, persisted and moves both balances")
    void balancedTransactionIsRecorded() {
        var cash = ledger.account("EUR", "ASSET");
        var customer = ledger.account("EUR", "LIABILITY");

        var location = client.post()
                .uri("/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body(line(cash, 2_500, "EUR", "DEBIT"), line(customer, 2_500, "EUR", "CREDIT")))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.lines.length()")
                .isEqualTo(2)
                .jsonPath("$.lines[0].accountId")
                .isEqualTo(cash.toString())
                .jsonPath("$.lines[0].amount.amount")
                .isEqualTo(2_500)
                .jsonPath("$.lines[0].direction")
                .isEqualTo("DEBIT")
                .jsonPath("$.lines[1].accountId")
                .isEqualTo(customer.toString())
                .jsonPath("$.lines[1].direction")
                .isEqualTo("CREDIT")
                .returnResult()
                .getResponseHeaders()
                .getLocation();

        var id = UUID.fromString(location.getPath().substring("/transactions/".length()));
        assertThat(jdbc.queryForObject("select count(*) from transactions where id = ?", Long.class, id))
                .isOne();
        assertThat(postingsOf(cash, customer)).isEqualTo(2);
        expectBalance(cash, 2_500);
        expectBalance(customer, 2_500);
    }

    @Test
    @DisplayName("AC2.2 the postings of an accepted transaction sum to zero per currency")
    void postingsOfAnAcceptedTransactionSumToZero() {
        var cash = ledger.account("EUR", "ASSET");
        var customer = ledger.account("EUR", "LIABILITY");
        var fees = ledger.account("EUR", "INCOME");

        var transaction = record(body(
                line(customer, 1_000, "EUR", "DEBIT"),
                line(cash, 950, "EUR", "CREDIT"),
                line(fees, 50, "EUR", "CREDIT")));

        assertThat(jdbc.queryForList(
                        "select currency, sum(amount) as total from postings where transaction_id = ? group by currency",
                        transaction))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.get("currency")).isEqualTo("EUR");
                    assertThat(((Number) row.get("total")).longValue()).isZero();
                });
    }

    @Test
    @DisplayName("AC2.1 an unbalanced transaction is a Problem Details 422 and nothing is persisted")
    void unbalancedTransactionIsRejected() {
        var cash = ledger.account("EUR", "ASSET");
        var customer = ledger.account("EUR", "LIABILITY");

        var transactionsBefore = transactionCount();

        expectUnprocessable(
                body(line(cash, 2_500, "EUR", "DEBIT"), line(customer, 2_400, "EUR", "CREDIT")),
                "Unbalanced transaction",
                "unbalanced");

        expectNothingPersisted(transactionsBefore, cash, customer);
    }

    @Test
    @DisplayName("AC2.4 lines in different currencies are a Problem Details 422 and nothing is persisted")
    void mixedCurrenciesAreRejected() {
        var euro = ledger.account("EUR", "ASSET");
        var krona = ledger.account("SEK", "LIABILITY");

        var transactionsBefore = transactionCount();

        expectUnprocessable(
                body(line(euro, 100, "EUR", "DEBIT"), line(krona, 100, "SEK", "CREDIT")),
                "Currency mismatch",
                "expected EUR but got SEK");

        expectNothingPersisted(transactionsBefore, euro, krona);
    }

    @Test
    @DisplayName("B1 a line in a currency other than its account's is a Problem Details 422 and nothing is persisted")
    void lineCurrencyOtherThanTheAccountsIsRejected() {
        var euro = ledger.account("EUR", "ASSET");
        var krona = ledger.account("SEK", "LIABILITY");

        var transactionsBefore = transactionCount();

        expectUnprocessable(
                body(line(euro, 100, "EUR", "DEBIT"), line(krona, 100, "EUR", "CREDIT")),
                "Currency mismatch",
                "expected SEK but got EUR");

        expectNothingPersisted(transactionsBefore, euro, krona);
    }

    @Test
    @DisplayName("B1 two lines on one account are a Problem Details 422 and nothing is persisted")
    void twoLinesOnOneAccountAreRejected() {
        var cash = ledger.account("EUR", "ASSET");

        var transactionsBefore = transactionCount();

        expectUnprocessable(
                body(line(cash, 100, "EUR", "DEBIT"), line(cash, 100, "EUR", "CREDIT")),
                "Invalid transaction",
                "more than one line on account " + cash);

        expectNothingPersisted(transactionsBefore, cash);
    }

    @Test
    @DisplayName("B1 an unknown account is a Problem Details 404 and nothing is persisted")
    void unknownAccountIsRejected() {
        var cash = ledger.account("EUR", "ASSET");
        var unknown = UUID.randomUUID();

        var transactionsBefore = transactionCount();

        client.post()
                .uri("/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body(line(cash, 100, "EUR", "DEBIT"), line(unknown, 100, "EUR", "CREDIT")))
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.detail")
                .isEqualTo("Account " + unknown + " does not exist");

        expectNothingPersisted(transactionsBefore, cash);
    }

    @Test
    @DisplayName("B1 a single line, a zero amount or a missing direction is a Problem Details 400")
    void malformedRequestsAreRejected() {
        var cash = ledger.account("EUR", "ASSET");
        var customer = ledger.account("EUR", "LIABILITY");

        var transactionsBefore = transactionCount();

        for (var malformed : new String[] {
            body(line(cash, 100, "EUR", "DEBIT")),
            body(line(cash, 0, "EUR", "DEBIT"), line(customer, 0, "EUR", "CREDIT")),
            "{\"lines\": [{\"accountId\": \"%s\", \"amount\": {\"amount\": 1, \"currency\": \"EUR\"}},".formatted(cash)
                    + " {\"accountId\": \"%s\", \"amount\": {\"amount\": 1, \"currency\": \"EUR\"}}]}"
                            .formatted(customer)
        }) {
            client.post()
                    .uri("/transactions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(malformed)
                    .exchange()
                    .expectStatus()
                    .isBadRequest()
                    .expectHeader()
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON);
        }

        expectNothingPersisted(transactionsBefore, cash, customer);
    }

    @Test
    @DisplayName("B3 a failure after the postings are written rolls back the whole transaction")
    void failureWhileApplyingCountersRollsBackEverything() {
        var cash = ledger.account("EUR", "ASSET");
        var customer = ledger.account("EUR", "LIABILITY");
        // Counters only, no postings: the next debit on cash overflows its bigint counter inside the use case.
        jdbc.update("update accounts set debits_posted = ? where id = ?", Long.MAX_VALUE, cash);
        var transactionsBefore = transactionCount();

        client.post()
                .uri("/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body(line(cash, 1, "EUR", "DEBIT"), line(customer, 1, "EUR", "CREDIT")))
                .exchange()
                .expectStatus()
                .is5xxServerError();

        assertThat(transactionCount()).isEqualTo(transactionsBefore);
        assertThat(postingsOf(cash, customer)).isZero();
        assertThat(counters(customer)).containsExactly(0L, 0L);
        assertThat(counters(cash)).containsExactly(Long.MAX_VALUE, 0L);
    }

    /** Records a transaction that must be accepted and returns its id, taken from the {@code Location} header. */
    private UUID record(String body) {
        var location = client.post()
                .uri("/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .returnResult()
                .getResponseHeaders()
                .getLocation();
        return UUID.fromString(location.getPath().substring("/transactions/".length()));
    }

    private void expectUnprocessable(String body, String title, String detail) {
        client.post()
                .uri("/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus()
                .isEqualTo(422)
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(422)
                .jsonPath("$.title")
                .isEqualTo(title)
                .jsonPath("$.detail")
                .value(String.class, value -> assertThat(value).contains(detail))
                .jsonPath("$.instance")
                .isEqualTo("/transactions");
    }

    /** No new transaction row, no posting on these accounts, and their counters still zero (they start with none). */
    private void expectNothingPersisted(long transactionsBefore, UUID... accounts) {
        assertThat(transactionCount()).isEqualTo(transactionsBefore);
        assertThat(postingsOf(accounts)).isZero();
        for (var account : accounts) {
            assertThat(counters(account)).containsExactly(0L, 0L);
        }
    }

    private long transactionCount() {
        return jdbc.queryForObject("select count(*) from transactions", Long.class);
    }

    private long postingsOf(UUID... accounts) {
        var count = 0L;
        for (var account : accounts) {
            count += jdbc.queryForObject("select count(*) from postings where account_id = ?", Long.class, account);
        }
        return count;
    }

    private Long[] counters(UUID account) {
        return jdbc.queryForObject(
                "select debits_posted, credits_posted from accounts where id = ?",
                (row, rowNum) -> new Long[] {row.getLong(1), row.getLong(2)},
                account);
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

    private static String body(String... lines) {
        return "{\"lines\": [" + String.join(", ", lines) + "]}";
    }

    private static String line(UUID account, long amount, String currency, String direction) {
        return "{\"accountId\": \"%s\", \"amount\": {\"amount\": %d, \"currency\": \"%s\"}, \"direction\": \"%s\"}"
                .formatted(account, amount, currency, direction);
    }
}
