package com.ledgerflow.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/** UC3 idempotent initiation (ADR 0010), over real HTTP against a real PostgreSQL. All data is fictitious. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class IdempotentInitiationIT {

    private static final int THREADS = 20;

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    @DisplayName("AC3.1 the same key and request twice create one payment and two identical responses")
    void retryWithTheSameKeyCreatesOnePaymentAndAnIdenticalResponse() throws Exception {
        var key = UUID.randomUUID().toString();
        var debtor = UUID.randomUUID();
        var body = body(debtor, UUID.randomUUID(), 2_500);

        var first = post(key, body);
        var second = post(key, body);

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(second.statusCode()).isEqualTo(201);
        assertThat(second.headers().firstValue("Location"))
                .isEqualTo(first.headers().firstValue("Location"));
        assertThat(json.readTree(second.body())).isEqualTo(json.readTree(first.body()));
        assertThat(paymentsOf(debtor)).isOne();
    }

    @Test
    @DisplayName("B2 a retry with the fields in another order and other whitespace is the same request")
    void retryWithReorderedFieldsIsTheSameRequest() throws Exception {
        var key = UUID.randomUUID().toString();
        var debtor = UUID.randomUUID();
        var creditor = UUID.randomUUID();

        var first = post(key, body(debtor, creditor, 2_500));
        var second = post(key, """
                {
                  "amount" : { "currency" : "EUR", "amount" : 2500 },
                  "creditorAccountId" : "%s",
                  "debtorAccountId" : "%s"
                }""".formatted(creditor, debtor));

        assertThat(second.statusCode()).isEqualTo(201);
        assertThat(json.readTree(second.body())).isEqualTo(json.readTree(first.body()));
        assertThat(paymentsOf(debtor)).isOne();
    }

    @Test
    @DisplayName("AC3.2 the same key with a different request is a 422 Problem Details and changes nothing")
    void reusedKeyWithADifferentRequestIsRejected() throws Exception {
        var key = UUID.randomUUID().toString();
        var debtor = UUID.randomUUID();
        var creditor = UUID.randomUUID();
        var first = post(key, body(debtor, creditor, 2_500));
        var before = jdbc.queryForMap("select * from payments where debtor_account_id = ?", debtor);

        var reused = post(key, body(debtor, creditor, 9_999));

        assertThat(reused.statusCode()).isEqualTo(422);
        assertThat(reused.headers().firstValue("Content-Type")).contains("application/problem+json");
        var problem = json.readTree(reused.body());
        assertThat(problem.get("title").asString()).isEqualTo("Idempotency key reused");
        assertThat(problem.get("detail").asString())
                .isEqualTo("Idempotency-Key '" + key + "' was already used for a different request");
        assertThat(paymentsOf(debtor)).isOne();
        assertThat(jdbc.queryForMap("select * from payments where debtor_account_id = ?", debtor))
                .isEqualTo(before);
        assertThat(first.statusCode()).isEqualTo(201);
    }

    @Test
    @DisplayName("ADR 0010 a request rejected by validation stores no key; the same key then initiates normally")
    void rejectedRequestStoresNoKey() throws Exception {
        var key = UUID.randomUUID().toString();
        var debtor = UUID.randomUUID();

        var rejected = post(key, body(debtor, debtor, 2_500));
        var accepted = post(key, body(debtor, UUID.randomUUID(), 2_500));

        assertThat(rejected.statusCode()).isEqualTo(422);
        assertThat(accepted.statusCode()).isEqualTo(201);
        assertThat(paymentsOf(debtor)).isOne();
    }

    @Test
    @DisplayName("B3 concurrent requests with the same key create exactly one payment and identical 201 responses")
    void concurrentRequestsWithTheSameKeyCreateOnePayment() throws Exception {
        var key = UUID.randomUUID().toString();
        var debtor = UUID.randomUUID();
        var body = body(debtor, UUID.randomUUID(), 2_500);
        var ready = new CountDownLatch(THREADS);
        var start = new CountDownLatch(1);

        var responses = new ArrayList<HttpResponse<String>>();
        try (var executor = Executors.newFixedThreadPool(THREADS)) {
            var futures = new ArrayList<Future<HttpResponse<String>>>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return post(key, body);
                }));
            }
            ready.await();
            start.countDown();
            for (var future : futures) {
                responses.add(future.get());
            }
        }

        assertThat(responses).extracting(HttpResponse::statusCode).containsOnly(201);
        var first = json.readTree(responses.getFirst().body());
        assertThat(responses)
                .allSatisfy(
                        response -> assertThat(json.readTree(response.body())).isEqualTo(first));
        assertThat(paymentsOf(debtor)).isOne();
    }

    private HttpResponse<String> post(String key, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/payments"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private long paymentsOf(UUID debtor) {
        return jdbc.queryForObject("select count(*) from payments where debtor_account_id = ?", Long.class, debtor);
    }

    private static String body(UUID debtor, UUID creditor, long amount) {
        return """
                {"debtorAccountId": "%s", "creditorAccountId": "%s", "amount": {"amount": %d, "currency": "EUR"}}""".formatted(debtor, creditor, amount);
    }
}
