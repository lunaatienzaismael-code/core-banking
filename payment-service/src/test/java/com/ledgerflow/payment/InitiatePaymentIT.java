package com.ledgerflow.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.json.JsonMapper;

/** UC3 initiate a payment and read it back, end to end over HTTP against a real PostgreSQL. All data is fictitious. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class InitiatePaymentIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private final JsonMapper json = JsonMapper.builder().build();
    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    @DisplayName("B3 an initiated payment is stored as INITIATED and its Location returns the same body")
    void initiatedPaymentIsStoredAndReadableAtItsLocation() {
        var debtor = UUID.randomUUID();
        var creditor = UUID.randomUUID();

        var created = client.post()
                .uri("/payments")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body(debtor, creditor, 2_500, "EUR"))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(String.class)
                .returnResult();
        var payment = json.readTree(created.getResponseBody());
        var id = UUID.fromString(payment.get("id").asString());

        assertThat(payment.get("status").asString()).isEqualTo("INITIATED");
        assertThat(payment.has("reason")).isFalse();
        assertThat(jdbc.queryForMap(
                        "select debtor_account_id, creditor_account_id, amount, currency, status, reason"
                                + " from payments where id = ?",
                        id))
                .containsEntry("debtor_account_id", debtor)
                .containsEntry("creditor_account_id", creditor)
                .containsEntry("amount", 2_500L)
                .containsEntry("currency", "EUR")
                .containsEntry("status", "INITIATED")
                .containsEntry("reason", null);

        var read = client.get()
                .uri(created.getResponseHeaders().getLocation())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        assertThat(json.readTree(read)).isEqualTo(payment);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "same account on both sides, 422, same",
        "zero amount, 400, zero",
        "unknown currency, 400, currency",
    })
    @DisplayName("B3 a rejected initiation leaves no payment")
    void rejectedInitiationLeavesNoPayment(String rejection, int status, String variant) {
        var debtor = UUID.randomUUID();
        var body = switch (variant) {
            case "same" -> body(debtor, debtor, 2_500, "EUR");
            case "zero" -> body(debtor, UUID.randomUUID(), 0, "EUR");
            default -> body(debtor, UUID.randomUUID(), 2_500, "ABC");
        };

        client.post()
                .uri("/payments")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus()
                .isEqualTo(status)
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);

        assertThat(jdbc.queryForObject("select count(*) from payments where debtor_account_id = ?", Long.class, debtor))
                .isZero();
    }

    @Test
    @DisplayName("B1 an unknown payment is a Problem Details 404")
    void unknownPaymentIsProblemDetails404() {
        var unknown = UUID.randomUUID();

        client.get()
                .uri("/payments/{id}", unknown)
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.detail")
                .isEqualTo("Payment " + unknown + " does not exist")
                .jsonPath("$.instance")
                .isEqualTo("/payments/" + unknown);
    }

    private static String body(UUID debtor, UUID creditor, long amount, String currency) {
        return """
                {"debtorAccountId": "%s", "creditorAccountId": "%s", "amount": {"amount": %d, "currency": "%s"}}""".formatted(debtor, creditor, amount, currency);
    }
}
