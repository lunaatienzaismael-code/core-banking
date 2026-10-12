package com.ledgerflow.ledger.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ledgerflow.ledger.application.port.in.AccountView;
import com.ledgerflow.ledger.application.port.in.OpenAccountCommand;
import com.ledgerflow.ledger.application.port.in.OpenAccountUseCase;
import com.ledgerflow.ledger.domain.AccountId;
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

    private MvcTestResult post(String body) {
        return mvc.post()
                .uri("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}
