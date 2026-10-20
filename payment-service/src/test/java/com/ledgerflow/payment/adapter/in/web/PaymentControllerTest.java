package com.ledgerflow.payment.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ledgerflow.payment.application.port.in.GetPaymentUseCase;
import com.ledgerflow.payment.application.port.in.InitiatePaymentCommand;
import com.ledgerflow.payment.application.port.in.InitiatePaymentUseCase;
import com.ledgerflow.payment.application.port.in.PaymentView;
import com.ledgerflow.payment.domain.AccountId;
import com.ledgerflow.payment.domain.InvalidPaymentException;
import com.ledgerflow.payment.domain.Money;
import com.ledgerflow.payment.domain.PaymentId;
import com.ledgerflow.payment.domain.PaymentNotFoundException;
import com.ledgerflow.payment.domain.PaymentStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    private static final UUID ID = UUID.fromString("7c4e9a10-0000-4000-8000-000000000001");
    private static final UUID DEBTOR = UUID.fromString("3f1c2b7e-0000-4000-8000-000000000001");
    private static final UUID CREDITOR = UUID.fromString("3f1c2b7e-0000-4000-8000-000000000002");
    private static final Instant JAN_15 = Instant.parse("2026-01-15T09:30:00Z");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private InitiatePaymentUseCase initiatePayment;

    @MockitoBean
    private GetPaymentUseCase getPayment;

    @Test
    void initiateReturns201WithLocationAndThePayment() {
        when(initiatePayment.initiate(any())).thenReturn(view(PaymentStatus.INITIATED, Optional.empty()));

        var result = post(body(DEBTOR, CREDITOR, 2_500, "EUR"));

        assertThat(result).hasStatus(HttpStatus.CREATED).hasHeader("Location", "http://localhost/payments/" + ID);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {"id": "7c4e9a10-0000-4000-8000-000000000001", "status": "INITIATED",
                 "debtorAccountId": "3f1c2b7e-0000-4000-8000-000000000001",
                 "creditorAccountId": "3f1c2b7e-0000-4000-8000-000000000002",
                 "amount": {"amount": 2500, "currency": "EUR"}, "initiatedAt": "2026-01-15T09:30:00Z"}""");
        verify(initiatePayment)
                .initiate(new InitiatePaymentCommand(
                        new AccountId(DEBTOR), new AccountId(CREDITOR), Money.of(2_500, "EUR")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", """
                {"creditorAccountId": "3f1c2b7e-0000-4000-8000-000000000002",
                 "amount": {"amount": 2500, "currency": "EUR"}}""", """
                {"debtorAccountId": "3f1c2b7e-0000-4000-8000-000000000001",
                 "amount": {"amount": 2500, "currency": "EUR"}}""", """
                {"debtorAccountId": "3f1c2b7e-0000-4000-8000-000000000001",
                 "creditorAccountId": "3f1c2b7e-0000-4000-8000-000000000002"}""", """
                {"debtorAccountId": "3f1c2b7e-0000-4000-8000-000000000001",
                 "creditorAccountId": "3f1c2b7e-0000-4000-8000-000000000002",
                 "amount": {"amount": 0, "currency": "EUR"}}""", """
                {"debtorAccountId": "3f1c2b7e-0000-4000-8000-000000000001",
                 "creditorAccountId": "3f1c2b7e-0000-4000-8000-000000000002",
                 "amount": {"amount": 2500, "currency": "eur"}}""", """
                {"debtorAccountId": "not-a-uuid",
                 "creditorAccountId": "3f1c2b7e-0000-4000-8000-000000000002",
                 "amount": {"amount": 2500, "currency": "EUR"}}""", "{not json"})
    void malformedRequestIs400ProblemDetailsAndNoUseCaseCall(String body) {
        var result = post(body);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(400);
        verifyNoInteractions(initiatePayment);
    }

    @Test
    void unknownCurrencyIs400ProblemDetails() {
        var result = post(body(DEBTOR, CREDITOR, 2_500, "ABC"));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Invalid currency");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Not an ISO-4217 currency code: 'ABC'");
        verifyNoInteractions(initiatePayment);
    }

    @Test
    void invalidPaymentIs422ProblemDetails() {
        when(initiatePayment.initiate(any()))
                .thenThrow(new InvalidPaymentException("Debtor and creditor are the same account " + DEBTOR));

        var result = post(body(DEBTOR, DEBTOR, 2_500, "EUR"));

        assertThat(result)
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(422);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Invalid payment");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Debtor and creditor are the same account " + DEBTOR);
    }

    @Test
    void getReturns200WithThePaymentAndItsReason() {
        when(getPayment.paymentOf(new PaymentId(ID))).thenReturn(view(PaymentStatus.REJECTED, Optional.of("risk")));

        var result = mvc.get().uri("/payments/{id}", ID).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("REJECTED");
        assertThat(result).bodyJson().extractingPath("$.reason").isEqualTo("risk");
    }

    @Test
    void getOfUnknownPaymentIs404ProblemDetails() {
        when(getPayment.paymentOf(new PaymentId(ID))).thenThrow(new PaymentNotFoundException(new PaymentId(ID)));

        var result = mvc.get().uri("/payments/{id}", ID).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Payment not found");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Payment " + ID + " does not exist");
    }

    @Test
    void getWithMalformedIdIs400ProblemDetails() {
        var result = mvc.get().uri("/payments/{id}", "12345").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        verifyNoInteractions(getPayment);
    }

    @Test
    void unexpectedFailureIsA500ProblemDetailsWithoutInternals() {
        when(getPayment.paymentOf(new PaymentId(ID)))
                .thenThrow(new IllegalStateException("connection pool exhausted at db-host-7"));

        var result = mvc.get().uri("/payments/{id}", ID).exchange();

        assertThat(result)
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("An unexpected error occurred.");
        assertThat(result).bodyText().doesNotContain("db-host-7").doesNotContain("IllegalStateException");
    }

    private static PaymentView view(PaymentStatus status, Optional<String> reason) {
        return new PaymentView(
                new PaymentId(ID),
                status,
                new AccountId(DEBTOR),
                new AccountId(CREDITOR),
                Money.of(2_500, "EUR"),
                JAN_15,
                reason);
    }

    private MvcTestResult post(String body) {
        return mvc.post()
                .uri("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static String body(UUID debtor, UUID creditor, long amount, String currency) {
        return """
                {"debtorAccountId": "%s", "creditorAccountId": "%s", "amount": {"amount": %d, "currency": "%s"}}""".formatted(debtor, creditor, amount, currency);
    }
}
