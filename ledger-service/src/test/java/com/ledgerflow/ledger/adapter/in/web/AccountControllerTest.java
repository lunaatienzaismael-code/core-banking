package com.ledgerflow.ledger.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ledgerflow.ledger.application.port.in.AccountBalanceView;
import com.ledgerflow.ledger.application.port.in.AccountView;
import com.ledgerflow.ledger.application.port.in.GetAccountBalanceUseCase;
import com.ledgerflow.ledger.application.port.in.OpenAccountCommand;
import com.ledgerflow.ledger.application.port.in.OpenAccountUseCase;
import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.AccountNotFoundException;
import com.ledgerflow.ledger.domain.AccountType;
import com.ledgerflow.ledger.domain.Money;
import com.ledgerflow.ledger.domain.OverdraftPolicy;
import com.ledgerflow.ledger.domain.Side;
import java.util.Currency;
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

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    private static final Currency EUR = Currency.getInstance("EUR");
    private static final UUID ID = UUID.fromString("3f1c2b7e-0000-4000-8000-000000000001");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private OpenAccountUseCase openAccount;

    @MockitoBean
    private GetAccountBalanceUseCase getAccountBalance;

    @Test
    void openAccountReturns201WithLocationAndTheNewAccount() {
        when(openAccount.open(any()))
                .thenReturn(new AccountView(
                        new AccountId(ID),
                        EUR,
                        AccountType.LIABILITY,
                        Side.CREDIT,
                        OverdraftPolicy.NOT_ALLOWED,
                        Money.zero(EUR)));

        var result = post("""
                {"currency": "EUR", "type": "LIABILITY", "overdraftPolicy": "NOT_ALLOWED"}""");

        assertThat(result)
                .hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", "http://localhost/accounts/" + ID)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"id": "3f1c2b7e-0000-4000-8000-000000000001", "currency": "EUR", "type": "LIABILITY",
                         "normalBalanceSide": "CREDIT", "overdraftPolicy": "NOT_ALLOWED",
                         "balance": {"amount": 0, "currency": "EUR"}}""");
        verify(openAccount).open(new OpenAccountCommand(EUR, AccountType.LIABILITY, OverdraftPolicy.NOT_ALLOWED));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"type\": \"LIABILITY\", \"overdraftPolicy\": \"NOT_ALLOWED\"}",
                "{\"currency\": \"eur\", \"type\": \"LIABILITY\", \"overdraftPolicy\": \"NOT_ALLOWED\"}",
                "{\"currency\": \"EUR\", \"type\": \"SAVINGS\", \"overdraftPolicy\": \"NOT_ALLOWED\"}",
                "{\"currency\": \"EUR\", \"type\": \"LIABILITY\"}",
                "{\"currency\": \"EUR\", \"type\": \"LIABILITY\", \"overdraftPolicy\": \"SOMETIMES\"}",
                "{\"currency\": \"EUR\", \"type\": \"LIABILITY\", \"overdraftPolicy\": ",
            })
    void invalidRequestIsRejectedAsProblemDetails(String body) {
        var result = post(body);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(400);
        verifyNoInteractions(openAccount);
    }

    @Test
    void wellFormedButUnknownCurrencyCodeIsRejectedAsProblemDetails() {
        var result = post("""
                {"currency": "ABC", "type": "LIABILITY", "overdraftPolicy": "NOT_ALLOWED"}""");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Invalid currency");
        assertThat(result).bodyJson().extractingPath("$.detail").asString().contains("ABC");
        verifyNoInteractions(openAccount);
    }

    @Test
    void balanceReturns200WithTheNormalSideAndTheBalance() {
        when(getAccountBalance.balanceOf(new AccountId(ID)))
                .thenReturn(new AccountBalanceView(new AccountId(ID), Side.CREDIT, Money.of(7_500, "EUR")));

        var result = mvc.get().uri("/accounts/{id}/balance", ID).exchange();

        assertThat(result).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        {"accountId": "3f1c2b7e-0000-4000-8000-000000000001", "normalBalanceSide": "CREDIT",
                         "balance": {"amount": 7500, "currency": "EUR"}}""");
    }

    @Test
    void balanceOfUnknownAccountIsNotFoundAsProblemDetails() {
        when(getAccountBalance.balanceOf(new AccountId(ID))).thenThrow(new AccountNotFoundException(new AccountId(ID)));

        var result = mvc.get().uri("/accounts/{id}/balance", ID).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(404);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Account not found");
        assertThat(result).bodyJson().extractingPath("$.detail").asString().contains(ID.toString());
    }

    @Test
    void balanceWithMalformedIdIsRejectedAsProblemDetails() {
        var result = mvc.get().uri("/accounts/{id}/balance", "not-a-uuid").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(400);
        verifyNoInteractions(getAccountBalance);
    }

    @Test
    void unexpectedFailureIsA500ProblemDetailsWithoutInternals() {
        when(getAccountBalance.balanceOf(new AccountId(ID)))
                .thenThrow(new IllegalStateException("connection pool exhausted at db-host-7"));

        var result = mvc.get().uri("/accounts/{id}/balance", ID).exchange();

        assertThat(result)
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(500);
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("An unexpected error occurred.");
        assertThat(result).bodyText().doesNotContain("db-host-7").doesNotContain("IllegalStateException");
    }

    private MvcTestResult post(String body) {
        return mvc.post()
                .uri("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}
