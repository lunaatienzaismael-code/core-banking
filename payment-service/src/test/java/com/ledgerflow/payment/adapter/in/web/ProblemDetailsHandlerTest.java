package com.ledgerflow.payment.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** No controller exists yet, so the fallback is exercised directly; T-021 proves it over HTTP. */
class ProblemDetailsHandlerTest {

    private final ProblemDetailsHandler handler = new ProblemDetailsHandler();

    @Test
    void unexpectedFailureIsA500ProblemDetailsWithoutInternals() {
        var problem = handler.unexpected(new IllegalStateException("connection pool exhausted at db-host-7"));

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail()).isEqualTo("An unexpected error occurred.");
        assertThat(problem.toString()).doesNotContain("db-host-7").doesNotContain("IllegalStateException");
    }
}
