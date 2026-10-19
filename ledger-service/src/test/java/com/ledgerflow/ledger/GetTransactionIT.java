package com.ledgerflow.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** UC2 read a recorded transaction back, end to end over HTTP against a real PostgreSQL. All data is fictitious. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class GetTransactionIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private final JsonMapper json = JsonMapper.builder().build();
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
    @DisplayName("B3 the Location of a recorded transaction returns its id, time and lines, ordered by posting id")
    void locationOfARecordedTransactionReturnsIt() {
        var customer = ledger.account("EUR", "LIABILITY");
        var cash = ledger.account("EUR", "ASSET");
        var fees = ledger.account("EUR", "INCOME");
        var created = client.post()
                .uri("/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"lines": [
                          {"accountId": "%s", "amount": {"amount": 1000, "currency": "EUR"}, "direction": "DEBIT"},
                          {"accountId": "%s", "amount": {"amount": 950, "currency": "EUR"}, "direction": "CREDIT"},
                          {"accountId": "%s", "amount": {"amount": 50, "currency": "EUR"}, "direction": "CREDIT"}
                        ]}""".formatted(customer, cash, fees))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(String.class)
                .returnResult();
        var recorded = json.readTree(created.getResponseBody());

        var read = client.get()
                .uri(created.getResponseHeaders().getLocation())
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        var transaction = json.readTree(read);

        assertThat(transaction.get("id")).isEqualTo(recorded.get("id"));
        assertThat(transaction.get("occurredAt")).isEqualTo(recorded.get("occurredAt"));
        assertThat(lines(transaction)).containsExactlyElementsOf(byPostingId(lines(recorded)));
    }

    @Test
    @DisplayName("B3 an unknown transaction is a Problem Details 404")
    void unknownTransactionIsProblemDetails404() {
        var unknown = UUID.randomUUID();

        client.get()
                .uri("/transactions/{id}", unknown)
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(404)
                .jsonPath("$.title")
                .isEqualTo("Transaction not found")
                .jsonPath("$.detail")
                .isEqualTo("Transaction " + unknown + " does not exist")
                .jsonPath("$.instance")
                .isEqualTo("/transactions/" + unknown);
    }

    @Test
    @DisplayName("B3 a malformed transaction id is a Problem Details 400")
    void malformedTransactionIdIsProblemDetails400() {
        client.get()
                .uri("/transactions/{id}", "12345")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(400)
                .jsonPath("$.instance")
                .isEqualTo("/transactions/12345");
    }

    private static ArrayList<JsonNode> lines(JsonNode transaction) {
        var lines = new ArrayList<JsonNode>();
        transaction.get("lines").forEach(lines::add);
        return lines;
    }

    /** PostgreSQL orders uuids by their unsigned bytes, which is the order of their lowercase text form. */
    private static ArrayList<JsonNode> byPostingId(ArrayList<JsonNode> lines) {
        var sorted = new ArrayList<>(lines);
        sorted.sort(Comparator.comparing(line -> line.get("postingId").asString()));
        return sorted;
    }
}
