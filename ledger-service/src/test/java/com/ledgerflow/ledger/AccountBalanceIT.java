package com.ledgerflow.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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

/** UC1 query balance, end to end over HTTP against a real PostgreSQL, with postings inserted by {@link LedgerFixture}. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AccountBalanceIT {

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
    @DisplayName("AC1.2 a debit-normal account reports Σ debits − Σ credits")
    void debitNormalAccountWithMixedPostingsReportsDebitsMinusCredits() {
        var cash = ledger.account("EUR", "ASSET");
        var customerA = ledger.account("EUR", "LIABILITY");
        var customerB = ledger.account("EUR", "LIABILITY");
        ledger.transfer(cash, customerA, 10_000, "EUR");
        ledger.transfer(customerB, cash, 3_000, "EUR");

        expectBalance(cash, "DEBIT", 7_000, "EUR");
    }

    @Test
    @DisplayName("AC1.2 a credit-normal account reports Σ credits − Σ debits")
    void creditNormalAccountWithMixedPostingsReportsCreditsMinusDebits() {
        var customer = ledger.account("EUR", "LIABILITY");
        var cash = ledger.account("EUR", "ASSET");
        ledger.transfer(cash, customer, 10_000, "EUR");
        ledger.transfer(customer, cash, 2_500, "EUR");

        expectBalance(customer, "CREDIT", 7_500, "EUR");
    }

    @Test
    @DisplayName("AC1.2 an account without postings has a zero balance in its currency")
    void accountWithoutPostingsHasZeroBalance() {
        var account = ledger.account("SEK", "INCOME");

        expectBalance(account, "CREDIT", 0, "SEK");
    }

    @Test
    @DisplayName("AC1.2 postings of other accounts are not counted")
    void postingsOfOtherAccountsAreNotCounted() {
        var customer = ledger.account("EUR", "LIABILITY");
        var otherCustomer = ledger.account("EUR", "LIABILITY");
        var cash = ledger.account("EUR", "ASSET");
        ledger.transfer(cash, customer, 1_000, "EUR");
        ledger.transfer(cash, otherCustomer, 50_000, "EUR");
        ledger.transfer(otherCustomer, cash, 20_000, "EUR");

        expectBalance(customer, "CREDIT", 1_000, "EUR");
    }

    @Test
    @DisplayName("AC1.4 an unknown account is a Problem Details 404")
    void unknownAccountIsProblemDetails404() {
        var unknown = UUID.randomUUID();

        client.get()
                .uri("/accounts/{id}/balance", unknown)
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(404)
                .jsonPath("$.detail")
                .isEqualTo("Account " + unknown + " does not exist")
                .jsonPath("$.instance")
                .isEqualTo("/accounts/" + unknown + "/balance");
    }

    @Test
    @DisplayName("B2 a malformed account id is a Problem Details 400")
    void malformedAccountIdIsProblemDetails400() {
        client.get()
                .uri("/accounts/{id}/balance", "12345")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(400);
    }

    @Test
    @DisplayName("ADR 0009 each account's counters equal the sums of its debit and credit postings")
    void countersEqualTheSumsOfEachAccountsPostings() {
        var cash = ledger.account("EUR", "ASSET");
        var customer = ledger.account("EUR", "LIABILITY");
        var other = ledger.account("EUR", "LIABILITY");
        ledger.transfer(cash, customer, 10_000, "EUR");
        ledger.transfer(customer, other, 3_000, "EUR");
        ledger.transfer(other, cash, 500, "EUR");

        for (var account : List.of(cash, customer, other)) {
            var counters = jdbc.queryForMap("select debits_posted, credits_posted from accounts where id = ?", account);
            var sums = jdbc.queryForMap(
                    "select coalesce(sum(amount) filter (where amount > 0), 0) as debits,"
                            + " coalesce(-sum(amount) filter (where amount < 0), 0) as credits"
                            + " from postings where account_id = ?",
                    account);
            assertThat(((Number) counters.get("debits_posted")).longValue())
                    .isEqualTo(((Number) sums.get("debits")).longValue());
            assertThat(((Number) counters.get("credits_posted")).longValue())
                    .isEqualTo(((Number) sums.get("credits")).longValue());
        }
    }

    private void expectBalance(UUID account, String side, long amount, String currency) {
        client.get()
                .uri("/accounts/{id}/balance", account)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.accountId")
                .isEqualTo(account.toString())
                .jsonPath("$.normalBalanceSide")
                .isEqualTo(side)
                .jsonPath("$.balance.amount")
                .isEqualTo(amount)
                .jsonPath("$.balance.currency")
                .isEqualTo(currency);
    }
}
