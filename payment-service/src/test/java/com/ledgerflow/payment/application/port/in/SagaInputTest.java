package com.ledgerflow.payment.application.port.in;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerflow.payment.domain.PaymentId;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SagaInputTest {

    static Stream<Arguments> everyInput() {
        return Stream.of(
                Arguments.of(
                        "PaymentInitiated", (BiFunction<UUID, PaymentId, SagaInput>) SagaInput.PaymentInitiated::new),
                Arguments.of("FundsReserved", (BiFunction<UUID, PaymentId, SagaInput>) SagaInput.FundsReserved::new),
                Arguments.of("TransferPosted", (BiFunction<UUID, PaymentId, SagaInput>) SagaInput.TransferPosted::new),
                Arguments.of("ReservationRefused", (BiFunction<UUID, PaymentId, SagaInput>) (messageId, paymentId) ->
                        new SagaInput.ReservationRefused(messageId, paymentId, "UNKNOWN_ACCOUNT", "-")),
                Arguments.of("PostingRefused", (BiFunction<UUID, PaymentId, SagaInput>) (messageId, paymentId) ->
                        new SagaInput.PostingRefused(messageId, paymentId, "UNKNOWN_ACCOUNT", "-")),
                Arguments.of("FundsReleased", (BiFunction<UUID, PaymentId, SagaInput>)
                        (messageId, paymentId) -> new SagaInput.FundsReleased(messageId, paymentId, "-")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyInput")
    void needsTheMessageIdToBeDeduplicated(String name, BiFunction<UUID, PaymentId, SagaInput> input) {
        assertThatThrownBy(() -> input.apply(null, PaymentId.random()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("messageId");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyInput")
    void needsThePaymentItMovesOn(String name, BiFunction<UUID, PaymentId, SagaInput> input) {
        assertThatThrownBy(() -> input.apply(UUID.randomUUID(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("paymentId");
    }

    @Test
    void aPostingRefusalNeedsItsReasonAndDetail() {
        assertThatThrownBy(() -> new SagaInput.PostingRefused(UUID.randomUUID(), PaymentId.random(), null, "-"))
                .hasMessage("reason");
        assertThatThrownBy(() ->
                        new SagaInput.PostingRefused(UUID.randomUUID(), PaymentId.random(), "UNKNOWN_ACCOUNT", null))
                .hasMessage("detail");
    }

    @Test
    void aReleaseNeedsTheReasonItCarried() {
        assertThatThrownBy(() -> new SagaInput.FundsReleased(UUID.randomUUID(), PaymentId.random(), null))
                .hasMessage("reason");
    }

    @Test
    void aRefusalNeedsItsReasonAndDetail() {
        assertThatThrownBy(() -> new SagaInput.ReservationRefused(UUID.randomUUID(), PaymentId.random(), null, "-"))
                .hasMessage("reason");
        assertThatThrownBy(() -> new SagaInput.ReservationRefused(
                        UUID.randomUUID(), PaymentId.random(), "UNKNOWN_ACCOUNT", null))
                .hasMessage("detail");
    }
}
