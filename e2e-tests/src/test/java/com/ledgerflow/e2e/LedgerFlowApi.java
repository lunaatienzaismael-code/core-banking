package com.ledgerflow.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/** The two services' public HTTP APIs, only as far as the end-to-end tests drive them. All data is fictitious. */
final class LedgerFlowApi {

    private static final Set<String> SETTLED = Set.of("COMPLETED", "REJECTED", "FAILED");
    // The saga is a few Kafka round trips through each outbox relay; generous for a cold CI runner.
    private static final Duration SETTLE_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient http = HttpClient.newHttpClient();
    private final String ledger;
    private final String payments;

    LedgerFlowApi(String ledgerUrl, String paymentUrl) {
        this.ledger = ledgerUrl;
        this.payments = paymentUrl;
    }

    /** A customer deposit account: a liability of the bank, never below zero. */
    String openCustomerAccount(String currency) {
        return openAccount(currency, "LIABILITY");
    }

    /** Credits {@code amount} to a customer account, debited to a bank asset account opened for it. */
    void deposit(String account, long amount, String currency) {
        var bank = openAccount(currency, "ASSET");
        post(ledger + "/transactions", null, """
                {"lines": [
                  {"accountId": "%s", "amount": {"amount": %d, "currency": "%s"}, "direction": "DEBIT"},
                  {"accountId": "%s", "amount": {"amount": %d, "currency": "%s"}, "direction": "CREDIT"}
                ]}""".formatted(bank, amount, currency, account, amount, currency));
    }

    /** The balance's amount, in minor units, from the account's normal side. */
    long balance(String account) {
        return get(ledger + "/accounts/" + account + "/balance").read("$.balance.amount", Long.class);
    }

    String initiatePayment(String debtor, String creditor, long amount, String currency) {
        return post(payments + "/payments", UUID.randomUUID().toString(), """
                        {"debtorAccountId": "%s", "creditorAccountId": "%s",
                         "amount": {"amount": %d, "currency": "%s"}}""".formatted(
                                debtor, creditor, amount, currency))
                .read("$.id");
    }

    /** The payment once it reaches a terminal status. */
    DocumentContext awaitSettled(String payment) {
        return await().atMost(SETTLE_TIMEOUT)
                .until(() -> get(payments + "/payments/" + payment), body -> SETTLED.contains(body.read("$.status")));
    }

    private String openAccount(String currency, String type) {
        return post(ledger + "/accounts", null, """
                        {"currency": "%s", "type": "%s", "overdraftPolicy": "NOT_ALLOWED"}""".formatted(currency, type)).read("$.id");
    }

    private DocumentContext post(String url, String idempotencyKey, String json) {
        var request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return send(request.build(), 201);
    }

    private DocumentContext get(String url) {
        return send(HttpRequest.newBuilder(URI.create(url)).GET().build(), 200);
    }

    private DocumentContext send(HttpRequest request, int expectedStatus) {
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode())
                    .as("%s %s → %s", request.method(), request.uri(), response.body())
                    .isEqualTo(expectedStatus);
            return JsonPath.parse(response.body());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
