package com.ledgerflow.ledger;

import static org.assertj.core.api.Assertions.assertThat;

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

/** UC1 open account, end to end over HTTP against a real PostgreSQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OpenAccountIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    @DisplayName("AC1.1 an opened account has a unique id, the given currency and a zero balance")
    void openedAccountHasUniqueIdGivenCurrencyAndZeroBalance() {
        var first = open("{\"currency\": \"EUR\", \"type\": \"LIABILITY\", \"overdraftPolicy\": \"NOT_ALLOWED\"}");
        var second = open("{\"currency\": \"EUR\", \"type\": \"LIABILITY\", \"overdraftPolicy\": \"NOT_ALLOWED\"}");

        assertThat(first).isNotEqualTo(second);
        assertThat(jdbc.queryForMap("select currency, type, overdraft_policy from accounts where id = ?::uuid", first))
                .containsEntry("currency", "EUR")
                .containsEntry("type", "LIABILITY")
                .containsEntry("overdraft_policy", "NOT_ALLOWED");
        assertThat(jdbc.queryForObject(
                        "select count(*) from accounts where id in (?::uuid, ?::uuid)", Long.class, first, second))
                .isEqualTo(2);
    }

    @Test
    void openAccountResponseCarriesTheAccountAndItsLocation() {
        client.post()
                .uri("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"currency\": \"SEK\", \"type\": \"ASSET\", \"overdraftPolicy\": \"ALLOWED\"}")
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .valueMatches("Location", "http://localhost:" + port + "/accounts/[0-9a-f-]{36}")
                .expectBody()
                .jsonPath("$.currency")
                .isEqualTo("SEK")
                .jsonPath("$.type")
                .isEqualTo("ASSET")
                .jsonPath("$.normalBalanceSide")
                .isEqualTo("DEBIT")
                .jsonPath("$.overdraftPolicy")
                .isEqualTo("ALLOWED")
                .jsonPath("$.balance.amount")
                .isEqualTo(0)
                .jsonPath("$.balance.currency")
                .isEqualTo("SEK");
    }

    @Test
    void unknownCurrencyIsRejectedAsProblemDetailsAndNothingIsStored() {
        var before = jdbc.queryForObject("select count(*) from accounts", Long.class);

        client.post()
                .uri("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"currency\": \"ABC\", \"type\": \"LIABILITY\", \"overdraftPolicy\": \"NOT_ALLOWED\"}")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(400);

        assertThat(jdbc.queryForObject("select count(*) from accounts", Long.class))
                .isEqualTo(before);
    }

    private String open(String body) {
        var location = client.post()
                .uri("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.balance.amount")
                .isEqualTo(0)
                .jsonPath("$.currency")
                .isEqualTo("EUR")
                .returnResult()
                .getResponseHeaders()
                .getLocation();
        return location.getPath().substring("/accounts/".length());
    }
}
