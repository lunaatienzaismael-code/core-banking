package com.ledgerflow.ledger.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ledgerflow.ledger.application.port.in.MovementView;
import com.ledgerflow.ledger.application.port.out.AccountRepository;
import com.ledgerflow.ledger.application.port.out.PostingRepository;
import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.AccountNotFoundException;
import com.ledgerflow.ledger.domain.AccountType;
import com.ledgerflow.ledger.domain.Money;
import com.ledgerflow.ledger.domain.Movement;
import com.ledgerflow.ledger.domain.OverdraftPolicy;
import com.ledgerflow.ledger.domain.PostingId;
import com.ledgerflow.ledger.domain.PostingLine;
import com.ledgerflow.ledger.domain.Side;
import com.ledgerflow.ledger.domain.TransactionId;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GetAccountMovementsServiceTest {

    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Instant JAN_15 = Instant.parse("2026-01-15T09:30:00Z");
    private static final Instant JAN_16 = Instant.parse("2026-01-16T14:00:00Z");

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final PostingRepository postings = mock(PostingRepository.class);
    private final GetAccountMovementsService service = new GetAccountMovementsService(accounts, postings);

    @Test
    void movementsCarryUnsignedAmountsWithTheirDirectionInRepositoryOrder() {
        var account = Account.open(EUR, AccountType.LIABILITY, OverdraftPolicy.NOT_ALLOWED);
        when(accounts.findById(account.id())).thenReturn(Optional.of(account));
        var deposit = line(account, -10_000);
        var withdrawal = line(account, 2_500);
        when(postings.movementsOf(account))
                .thenReturn(List.of(new Movement(deposit, JAN_15), new Movement(withdrawal, JAN_16)));

        var movements = service.movementsOf(account.id());

        assertThat(movements)
                .containsExactly(
                        new MovementView(
                                deposit.id(), deposit.transactionId(), Money.of(10_000, "EUR"), Side.CREDIT, JAN_15),
                        new MovementView(
                                withdrawal.id(),
                                withdrawal.transactionId(),
                                Money.of(2_500, "EUR"),
                                Side.DEBIT,
                                JAN_16));
    }

    @Test
    void accountWithoutPostingsHasNoMovements() {
        var account = Account.open(EUR, AccountType.ASSET, OverdraftPolicy.ALLOWED);
        when(accounts.findById(account.id())).thenReturn(Optional.of(account));
        when(postings.movementsOf(account)).thenReturn(List.of());

        assertThat(service.movementsOf(account.id())).isEmpty();
    }

    @Test
    void unknownAccountIsReportedAsNotFoundWithoutReadingPostings() {
        var id = AccountId.random();
        when(accounts.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.movementsOf(id))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(id.toString());
        verifyNoInteractions(postings);
    }

    private static PostingLine line(Account account, long amount) {
        return new PostingLine(PostingId.random(), TransactionId.random(), account.id(), Money.of(amount, "EUR"));
    }
}
