package com.ledgerflow.ledger.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ledgerflow.ledger.application.port.in.RecordTransactionCommand;
import com.ledgerflow.ledger.application.port.in.TransactionView;
import com.ledgerflow.ledger.application.port.out.AccountRepository;
import com.ledgerflow.ledger.application.port.out.BalanceRepository;
import com.ledgerflow.ledger.application.port.out.TransactionRepository;
import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.AccountNotFoundException;
import com.ledgerflow.ledger.domain.AccountType;
import com.ledgerflow.ledger.domain.CurrencyMismatchException;
import com.ledgerflow.ledger.domain.Money;
import com.ledgerflow.ledger.domain.OverdraftPolicy;
import com.ledgerflow.ledger.domain.Side;
import com.ledgerflow.ledger.domain.Transaction;
import com.ledgerflow.ledger.domain.UnbalancedTransactionException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RecordTransactionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-18T09:30:00Z");

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final BalanceRepository balances = mock(BalanceRepository.class);
    private final RecordTransactionService service =
            new RecordTransactionService(accounts, transactions, balances, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void savesTheTransactionWithSignedLinesAtTheCurrentTime() {
        var cash = stored("EUR", AccountType.ASSET);
        var customer = stored("EUR", AccountType.LIABILITY);

        service.record(command(line(cash, 2_500, "EUR"), line(customer, -2_500, "EUR")));

        var saved = ArgumentCaptor.forClass(Transaction.class);
        verify(transactions).save(saved.capture());
        assertThat(saved.getValue().occurredAt()).isEqualTo(NOW);
        assertThat(saved.getValue().amountsByAccount())
                .containsEntry(cash.id(), Money.of(2_500, "EUR"))
                .containsEntry(customer.id(), Money.of(-2_500, "EUR"));
    }

    @Test
    void appliesEachLineToItsAccountInAscendingAccountIdOrderAfterSavingThePostings() {
        var low = stored(new UUID(0, 1), "EUR", AccountType.LIABILITY);
        var high = stored(new UUID(0, 2), "EUR", AccountType.ASSET);

        service.record(command(line(high, 700, "EUR"), line(low, -700, "EUR")));

        var order = inOrder(transactions, balances);
        order.verify(transactions).save(any());
        order.verify(balances).apply(low.id(), Money.of(-700, "EUR"));
        order.verify(balances).apply(high.id(), Money.of(700, "EUR"));
    }

    @Test
    void returnsTheRecordedLinesAsUnsignedAmountsWithTheirDirection() {
        var cash = stored("EUR", AccountType.ASSET);
        var customer = stored("EUR", AccountType.LIABILITY);

        var view = service.record(command(line(cash, 2_500, "EUR"), line(customer, -2_500, "EUR")));

        assertThat(view.occurredAt()).isEqualTo(NOW);
        assertThat(view.lines())
                .extracting(
                        TransactionView.Line::accountId, TransactionView.Line::amount, TransactionView.Line::direction)
                .containsExactly(
                        tuple(cash.id(), Money.of(2_500, "EUR"), Side.DEBIT),
                        tuple(customer.id(), Money.of(2_500, "EUR"), Side.CREDIT));
    }

    @Test
    void unbalancedLinesAreRejectedBeforeAnyPortIsUsed() {
        var cash = AccountId.random();
        var customer = AccountId.random();
        var command = new RecordTransactionCommand(List.of(
                new RecordTransactionCommand.Line(cash, Money.of(2_500, "EUR")),
                new RecordTransactionCommand.Line(customer, Money.of(-2_400, "EUR"))));

        assertThatThrownBy(() -> service.record(command)).isInstanceOf(UnbalancedTransactionException.class);
        verifyNoInteractions(accounts, transactions, balances);
    }

    @Test
    void unknownAccountIsRejectedWithoutWritingAnything() {
        var cash = stored("EUR", AccountType.ASSET);
        var unknown = AccountId.random();
        when(accounts.findById(unknown)).thenReturn(Optional.empty());
        var command = new RecordTransactionCommand(
                List.of(line(cash, 100, "EUR"), new RecordTransactionCommand.Line(unknown, Money.of(-100, "EUR"))));

        assertThatThrownBy(() -> service.record(command))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(unknown.toString());
        verifyNoInteractions(transactions, balances);
    }

    @Test
    void lineInACurrencyOtherThanItsAccountsIsRejectedWithoutWritingAnything() {
        var cash = stored("EUR", AccountType.ASSET);
        var krona = stored("SEK", AccountType.LIABILITY);

        assertThatThrownBy(() -> service.record(command(line(cash, 100, "EUR"), line(krona, -100, "EUR"))))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessageContaining("expected SEK but got EUR");
        verifyNoInteractions(transactions, balances);
    }

    private Account stored(String currency, AccountType type) {
        return stored(UUID.randomUUID(), currency, type);
    }

    private Account stored(UUID id, String currency, AccountType type) {
        var account = new Account(new AccountId(id), Currency.getInstance(currency), type, OverdraftPolicy.ALLOWED);
        when(accounts.findById(account.id())).thenReturn(Optional.of(account));
        return account;
    }

    private static RecordTransactionCommand.Line line(Account account, long amount, String currency) {
        return new RecordTransactionCommand.Line(account.id(), Money.of(amount, currency));
    }

    private static RecordTransactionCommand command(RecordTransactionCommand.Line... lines) {
        return new RecordTransactionCommand(List.of(lines));
    }
}
