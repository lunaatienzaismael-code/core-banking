package com.ledgerflow.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IdempotencyKeyTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 36, 255})
    void keyOfOneTo255CharactersIsAccepted(int length) {
        assertThat(new IdempotencyKey("k".repeat(length)).value()).hasSize(length);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 256})
    void keyOutsideOneTo255CharactersIsRejected(int length) {
        assertThatThrownBy(() -> new IdempotencyKey("k".repeat(length)))
                .isInstanceOf(InvalidIdempotencyKeyException.class)
                .hasMessage("Idempotency-Key must be 1 to 255 characters, got " + length);
    }

    @Test
    void missingKeyIsAProgrammingError() {
        assertThatThrownBy(() -> new IdempotencyKey(null)).isInstanceOf(NullPointerException.class);
    }
}
