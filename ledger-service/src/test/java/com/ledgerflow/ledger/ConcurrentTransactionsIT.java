package com.ledgerflow.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * UC2 AC2.5 safe concurrency: real threads released together by a start gate, real HTTP, real PostgreSQL. The outcome
 * is deterministic even though the interleaving is not (java-testing rule 8). All data is fictitious.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ConcurrentTransactionsIT {

    private static final int THREADS = 50;
    private static final long AMOUNT = 100;

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private LedgerFixture ledger;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        ledger = new LedgerFixture(jdbc);
    }

    @Test
    @DisplayName("AC2.5 concurrent withdrawals: exactly the funded ones are accepted, none is lost, no overdraft")
    void concurrentWithdrawalsAcceptExactlyTheFundedOnes() throws Exception {
        var funded = 20;
        var cash = ledger.account("EUR", "ASSET");
        var customer = ledger.account("EUR", "LIABILITY", "NOT_ALLOWED");
        var settlement = ledger.account("EUR", "LIABILITY", "ALLOWED");
        ledger.transfer(cash, customer, funded * AMOUNT, "EUR");

        var statuses = concurrently(THREADS, i -> transfer(customer, settlement));

        assertThat(statuses).containsOnlyKeys(201, 422);
        assertThat(statuses.get(201)).isEqualTo(funded);
        assertThat(statuses.get(422)).isEqualTo(THREADS - funded);
        assertThat(counters(customer)).containsExactly(funded * AMOUNT, funded * AMOUNT);
        assertThat(counters(settlement)).containsExactly(0L, funded * AMOUNT);
        assertCountersEqualPostings(cash, customer, settlement);
        assertPostingsSumToZeroPerCurrency();
    }

    @Test
    @DisplayName("AC2.5 concurrent transfers in both directions between two accounts all succeed, without deadlocks")
    void concurrentOpposingTransfersNeitherDeadlockNorLoseUpdates() throws Exception {
        var alice = ledger.account("EUR", "LIABILITY", "ALLOWED");
        var bob = ledger.account("EUR", "LIABILITY", "ALLOWED");

        // Even threads move alice → bob, odd ones bob → alice: opposite lock orders unless writers sort by account id.
        var statuses = concurrently(THREADS, i -> i % 2 == 0 ? transfer(alice, bob) : transfer(bob, alice));

        assertThat(statuses).containsExactly(Map.entry(201, THREADS));
        var each = THREADS / 2 * AMOUNT;
        assertThat(counters(alice)).containsExactly(each, each);
        assertThat(counters(bob)).containsExactly(each, each);
        assertCountersEqualPostings(alice, bob);
        assertPostingsSumToZeroPerCurrency();
    }

    /** Runs {@code count} requests on as many threads, all released at once; returns how many got each HTTP status. */
    private Map<Integer, Integer> concurrently(int count, Function<Integer, HttpRequest> request) throws Exception {
        var ready = new CountDownLatch(count);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(count)) {
            var results = new ArrayList<Future<Integer>>();
            for (int i = 0; i < count; i++) {
                var call = request.apply(i);
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return http.send(call, HttpResponse.BodyHandlers.discarding())
                            .statusCode();
                }));
            }
            ready.await();
            start.countDown();
            var statuses = new ArrayList<Integer>();
            for (var result : results) {
                statuses.add(result.get());
            }
            return statuses.stream().collect(Collectors.toMap(status -> status, status -> 1, Integer::sum));
        }
    }

    /** Debits {@code from} and credits {@code to} by {@link #AMOUNT}. */
    private HttpRequest transfer(UUID from, UUID to) {
        var body = """
                {"lines": [
                  {"accountId": "%s", "amount": {"amount": %d, "currency": "EUR"}, "direction": "DEBIT"},
                  {"accountId": "%s", "amount": {"amount": %d, "currency": "EUR"}, "direction": "CREDIT"}
                ]}""".formatted(from, AMOUNT, to, AMOUNT);
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/transactions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private List<Long> counters(UUID account) {
        return jdbc.queryForObject(
                "select debits_posted, credits_posted from accounts where id = ?",
                (row, rowNum) -> List.of(row.getLong(1), row.getLong(2)),
                account);
    }

    private void assertCountersEqualPostings(UUID... accounts) {
        for (var account : accounts) {
            var sums = jdbc.queryForObject(
                    "select coalesce(sum(amount) filter (where amount > 0), 0),"
                            + " coalesce(-sum(amount) filter (where amount < 0), 0)"
                            + " from postings where account_id = ?",
                    (row, rowNum) -> List.of(row.getLong(1), row.getLong(2)),
                    account);
            assertThat(counters(account)).as("counters of %s", account).isEqualTo(sums);
        }
    }

    private void assertPostingsSumToZeroPerCurrency() {
        assertThat(jdbc.queryForList(
                        "select currency from postings group by currency having sum(amount) <> 0", String.class))
                .isEmpty();
    }
}
