package com.ledgerflow.ledger.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ledgerflow.ledger.application.port.in.ReserveFundsCommand;
import com.ledgerflow.ledger.application.port.out.AccountRepository;
import com.ledgerflow.ledger.application.port.out.BalanceRepository;
import com.ledgerflow.ledger.application.port.out.MessageInbox;
import com.ledgerflow.ledger.application.port.out.ReservationReplies;
import com.ledgerflow.ledger.application.port.out.ReservationReplies.Refusal;
import com.ledgerflow.ledger.application.port.out.TransactionRepository;
import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.AccountType;
import com.ledgerflow.ledger.domain.InvalidTransactionException;
import com.ledgerflow.ledger.domain.Money;
import com.ledgerflow.ledger.domain.OverdraftPolicy;
import com.ledgerflow.ledger.domain.Transaction;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.support.TransactionOperations;

/**
 * The reservation runs through the real {@link RecordTransactionService}; only outbound ports are mocked. Transactions
 * are not simulated here: atomicity of the attempt and of the refusal is proven by {@code ReserveFundsIT}.
 */
class ReserveFundsServiceTest {

    private static final Currency EUR = Currency.getInstance("EUR");
    private static final String PAYMENT = "5f0c7a52-6f58-4a43-9a8e-2b0b5c1d7e11";
    private static final Account DEBTOR = Account.open(EUR, AccountType.LIABILITY, OverdraftPolicy.NOT_ALLOWED);
    private static final Account TRANSIT = Account.paymentsInTransit(EUR);

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final BalanceRepository balances = mock(BalanceRepository.class);
    private final MessageInbox inbox = mock(MessageInbox.class);
    private final ReservationReplies replies = mock(ReservationReplies.class);
    private final ReserveFundsService service = new ReserveFundsService(
            inbox,
            accounts,
            new RecordTransactionService(
                    accounts,
                    transactions,
                    balances,
                    Clock.fixed(Instant.parse("2026-10-28T09:00:00Z"), ZoneOffset.UTC)),
            replies,
            TransactionOperations.withoutTransaction());

    @BeforeEach
    void storedAccountsAndAFirstDeliveryUnlessATestSaysOtherwise() {
        when(accounts.findById(DEBTOR.id())).thenReturn(Optional.of(DEBTOR));
        when(accounts.findById(TRANSIT.id())).thenReturn(Optional.of(TRANSIT));
        when(balances.apply(any(), any())).thenReturn(true);
        when(inbox.firstDelivery(any())).thenReturn(true);
    }

    @Test
    @DisplayName("ADR 0012 a reservation debits the debtor and credits the transit account, then replies FundsReserved")
    void reservationMovesTheAmountToTheTransitAccountAndReplies() {
        service.reserve(command(2_500, "EUR"));

        var saved = ArgumentCaptor.forClass(Transaction.class);
        verify(transactions).save(saved.capture());
        assertThat(saved.getValue().lines())
                .extracting(line -> line.accountId(), line -> line.amount())
                .containsExactlyInAnyOrder(
                        tuple(DEBTOR.id(), Money.of(2_500, "EUR")), tuple(TRANSIT.id(), Money.of(-2_500, "EUR")));
        verify(accounts).saveIfAbsent(TRANSIT);
        verify(replies).fundsReserved(PAYMENT, saved.getValue().id());
        verify(replies, never()).reservationRefused(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("AC4.2 a redelivered command records nothing and replies nothing")
    void redeliveredCommandChangesNothing() {
        when(inbox.firstDelivery(any())).thenReturn(false);

        service.reserve(command(2_500, "EUR"));

        verifyNoInteractions(transactions, balances, replies);
        verify(accounts, never()).saveIfAbsent(any());
    }

    @Test
    @DisplayName("B3 insufficient funds is refused with INSUFFICIENT_FUNDS")
    void insufficientFundsIsRefused() {
        when(balances.apply(eq(DEBTOR.id()), any())).thenReturn(false);

        service.reserve(command(2_500, "EUR"));

        verify(replies)
                .reservationRefused(
                        eq(PAYMENT),
                        eq(Refusal.INSUFFICIENT_FUNDS),
                        eq("Account " + DEBTOR.id() + " has insufficient funds for a posting of "
                                + Money.of(2_500, "EUR")));
        verify(replies, never()).fundsReserved(anyString(), any());
    }

    @Test
    @DisplayName("B3 an unknown debtor is refused with UNKNOWN_ACCOUNT")
    void unknownDebtorIsRefused() {
        var unknown = AccountId.random();
        when(accounts.findById(unknown)).thenReturn(Optional.empty());

        service.reserve(new ReserveFundsCommand(UUID.randomUUID(), PAYMENT, unknown, Money.of(2_500, "EUR")));

        verify(replies).reservationRefused(eq(PAYMENT), eq(Refusal.UNKNOWN_ACCOUNT), anyString());
        verify(transactions, never()).save(any());
    }

    @Test
    @DisplayName("B3 an amount in another currency than the debtor's is refused with CURRENCY_MISMATCH")
    void currencyMismatchIsRefused() {
        service.reserve(command(2_500, "USD"));

        verify(replies).reservationRefused(eq(PAYMENT), eq(Refusal.CURRENCY_MISMATCH), anyString());
        verify(transactions, never()).save(any());
    }

    @Test
    @DisplayName("AC4.2 a refusal is recorded in the inbox, so its redelivery replies nothing")
    void refusalIsRecordedSoItsRedeliveryRepliesNothing() {
        when(balances.apply(eq(DEBTOR.id()), any())).thenReturn(false);
        var command = command(2_500, "EUR");
        // The attempt and the refusal each see a first delivery (the attempt rolled back); the redelivery does not.
        when(inbox.firstDelivery(command.messageId())).thenReturn(true, true, false);

        service.reserve(command);
        service.reserve(command);

        verify(inbox, times(3)).firstDelivery(command.messageId());
        verify(replies).reservationRefused(eq(PAYMENT), eq(Refusal.INSUFFICIENT_FUNDS), anyString());
    }

    @Test
    @DisplayName("a technical failure is not a refusal: it propagates so the message is retried")
    void technicalFailurePropagatesWithoutAReply() {
        when(balances.apply(any(), any())).thenThrow(new QueryTimeoutException("database busy"));

        assertThatThrownBy(() -> service.reserve(command(2_500, "EUR"))).isInstanceOf(QueryTimeoutException.class);
        verifyNoInteractions(replies);
    }

    @Test
    void anAmountToReserveMustBePositive() {
        assertThatThrownBy(() -> command(0, "EUR"))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("Amount to reserve must be positive, got " + Money.of(0, "EUR"));
        assertThatThrownBy(() -> command(-1, "EUR")).isInstanceOf(InvalidTransactionException.class);
    }

    private static ReserveFundsCommand command(long amount, String currency) {
        return new ReserveFundsCommand(UUID.randomUUID(), PAYMENT, DEBTOR.id(), Money.of(amount, currency));
    }
}
