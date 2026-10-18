package com.ledgerflow.ledger.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ledgerflow.ledger.application.port.out.AccountRepository;
import com.ledgerflow.ledger.application.port.out.BalanceRepository;
import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.AccountNotFoundException;
import com.ledgerflow.ledger.domain.AccountType;
import com.ledgerflow.ledger.domain.Money;
import com.ledgerflow.ledger.domain.OverdraftPolicy;
import com.ledgerflow.ledger.domain.Side;
import java.util.Currency;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GetAccountBalanceServiceTest {

    private static final Currency EUR = Currency.getInstance("EUR");

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final BalanceRepository balances = mock(BalanceRepository.class);
    private final GetAccountBalanceService service = new GetAccountBalanceService(accounts, balances);

    @Test
    void debitNormalAccountReportsTheSignedNetAsIs() {
        var account = stored(AccountType.ASSET);
        when(balances.signedNetOf(account)).thenReturn(Money.of(7_000, "EUR"));

        var view = service.balanceOf(account.id());

        assertThat(view.accountId()).isEqualTo(account.id());
        assertThat(view.normalBalanceSide()).isEqualTo(Side.DEBIT);
        assertThat(view.balance()).isEqualTo(Money.of(7_000, "EUR"));
    }

    @Test
    void creditNormalAccountReportsTheNegatedSignedNet() {
        var account = stored(AccountType.LIABILITY);
        when(balances.signedNetOf(account)).thenReturn(Money.of(-7_500, "EUR"));

        var view = service.balanceOf(account.id());

        assertThat(view.normalBalanceSide()).isEqualTo(Side.CREDIT);
        assertThat(view.balance()).isEqualTo(Money.of(7_500, "EUR"));
    }

    @Test
    void unknownAccountIsReportedAsNotFoundWithoutReadingTheBalance() {
        var id = AccountId.random();
        when(accounts.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.balanceOf(id))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(id.toString());
        verifyNoInteractions(balances);
    }

    private Account stored(AccountType type) {
        var account = Account.open(EUR, type, OverdraftPolicy.ALLOWED);
        when(accounts.findById(account.id())).thenReturn(Optional.of(account));
        return account;
    }
}
