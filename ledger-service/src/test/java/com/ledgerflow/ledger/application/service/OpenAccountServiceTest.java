package com.ledgerflow.ledger.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ledgerflow.ledger.application.port.in.OpenAccountCommand;
import com.ledgerflow.ledger.application.port.out.AccountRepository;
import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.AccountType;
import com.ledgerflow.ledger.domain.Money;
import com.ledgerflow.ledger.domain.OverdraftPolicy;
import com.ledgerflow.ledger.domain.Side;
import java.util.Currency;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OpenAccountServiceTest {

    private static final Currency EUR = Currency.getInstance("EUR");

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final OpenAccountService service = new OpenAccountService(accounts);

    @Test
    void savesTheOpenedAccountWithTheRequestedAttributes() {
        var view = service.open(new OpenAccountCommand(EUR, AccountType.LIABILITY, OverdraftPolicy.NOT_ALLOWED));

        var saved = ArgumentCaptor.forClass(Account.class);
        verify(accounts).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(view.id());
        assertThat(saved.getValue().currency()).isEqualTo(EUR);
        assertThat(saved.getValue().type()).isEqualTo(AccountType.LIABILITY);
        assertThat(saved.getValue().overdraftPolicy()).isEqualTo(OverdraftPolicy.NOT_ALLOWED);
    }

    @Test
    void openedAccountHasZeroBalanceInItsCurrencyAndTheNormalSideOfItsType() {
        var view = service.open(new OpenAccountCommand(EUR, AccountType.LIABILITY, OverdraftPolicy.NOT_ALLOWED));

        assertThat(view.balance()).isEqualTo(Money.zero(EUR));
        assertThat(view.normalBalanceSide()).isEqualTo(Side.CREDIT);
        assertThat(view.currency()).isEqualTo(EUR);
    }
}
