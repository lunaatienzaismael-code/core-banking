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

import com.ledgerflow.ledger.application.port.in.PostTransferCommand;
import com.ledgerflow.ledger.application.port.in.ReleaseFundsCommand;
import com.ledgerflow.ledger.application.port.in.ReserveFundsCommand;
import com.ledgerflow.ledger.application.port.out.AccountRepository;
import com.ledgerflow.ledger.application.port.out.BalanceRepository;
import com.ledgerflow.ledger.application.port.out.MessageInbox;
import com.ledgerflow.ledger.application.port.out.TransactionRepository;
import com.ledgerflow.ledger.application.port.out.TransferReplies;
import com.ledgerflow.ledger.application.port.out.TransferReplies.Refusal;
import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.AccountNotFoundException;
import com.ledgerflow.ledger.domain.AccountType;
import com.ledgerflow.ledger.domain.InsufficientFundsException;
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
 * Reserve, post and release run through the real {@link RecordTransactionService}; only outbound ports are mocked.
 * Transactions are not simulated here: atomicity of each attempt and of each refusal is proven by
 * {@code PaymentTransfersIT}.
 */
class PaymentTransferServiceTest {

    private static final Currency EUR = Currency.getInstance("EUR");
    private static final String PAYMENT = "5f0c7a52-6f58-4a43-9a8e-2b0b5c1d7e11";
    private static final Account DEBTOR = Account.open(EUR, AccountType.LIABILITY, OverdraftPolicy.NOT_ALLOWED);
    private static final Account CREDITOR = Account.open(EUR, AccountType.LIABILITY, OverdraftPolicy.NOT_ALLOWED);
    private static final Account TRANSIT = Account.paymentsInTransit(EUR);

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final BalanceRepository balances = mock(BalanceRepository.class);
    private final MessageInbox inbox = mock(MessageInbox.class);
    private final TransferReplies replies = mock(TransferReplies.class);
    private final PaymentTransferService service = new PaymentTransferService(
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
        for (var account : new Account[] {DEBTOR, CREDITOR, TRANSIT}) {
            when(accounts.findById(account.id())).thenReturn(Optional.of(account));
        }
        when(balances.apply(any(), any())).thenReturn(true);
        when(inbox.firstDelivery(any())).thenReturn(true);
    }

    // ---- reserve ----

    @Test
    @DisplayName("ADR 0012 a reservation debits the debtor and credits the transit account, then replies FundsReserved")
    void reservationMovesTheAmountToTheTransitAccountAndReplies() {
        service.reserve(reserve(2_500, "EUR"));

        var saved = savedTransaction();
        assertThat(saved.lines())
                .extracting(line -> line.accountId(), line -> line.amount())
                .containsExactlyInAnyOrder(
                        tuple(DEBTOR.id(), Money.of(2_500, "EUR")), tuple(TRANSIT.id(), Money.of(-2_500, "EUR")));
        verify(accounts).saveIfAbsent(TRANSIT);
        verify(replies).fundsReserved(PAYMENT, saved.id());
        verify(replies, never()).reservationRefused(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("AC4.2 a redelivered ReserveFunds records nothing and replies nothing")
    void redeliveredReservationChangesNothing() {
        when(inbox.firstDelivery(any())).thenReturn(false);

        service.reserve(reserve(2_500, "EUR"));

        verifyNoInteractions(transactions, balances, replies);
        verify(accounts, never()).saveIfAbsent(any());
    }

    @Test
    @DisplayName("B3 (T-027) insufficient funds is refused with INSUFFICIENT_FUNDS")
    void insufficientFundsIsRefused() {
        when(balances.apply(eq(DEBTOR.id()), any())).thenReturn(false);

        service.reserve(reserve(2_500, "EUR"));

        verify(replies)
                .reservationRefused(
                        PAYMENT,
                        Refusal.INSUFFICIENT_FUNDS,
                        "Account " + DEBTOR.id() + " has insufficient funds for a posting of "
                                + Money.of(2_500, "EUR"));
        verify(replies, never()).fundsReserved(anyString(), any());
    }

    @Test
    @DisplayName("B3 (T-027) an unknown debtor is refused with UNKNOWN_ACCOUNT")
    void unknownDebtorIsRefused() {
        var unknown = AccountId.random();
        when(accounts.findById(unknown)).thenReturn(Optional.empty());

        service.reserve(new ReserveFundsCommand(UUID.randomUUID(), PAYMENT, unknown, Money.of(2_500, "EUR")));

        verify(replies)
                .reservationRefused(
                        PAYMENT, Refusal.UNKNOWN_ACCOUNT, new AccountNotFoundException(unknown).getMessage());
        verify(transactions, never()).save(any());
    }

    @Test
    @DisplayName("B3 (T-027) an amount in another currency than the debtor's is refused with CURRENCY_MISMATCH")
    void currencyMismatchIsRefused() {
        service.reserve(reserve(2_500, "USD"));

        verify(replies).reservationRefused(eq(PAYMENT), eq(Refusal.CURRENCY_MISMATCH), anyString());
        verify(transactions, never()).save(any());
    }

    @Test
    @DisplayName("AC4.2 a refusal is recorded in the inbox, so its redelivery replies nothing")
    void refusalIsRecordedSoItsRedeliveryRepliesNothing() {
        when(balances.apply(eq(DEBTOR.id()), any())).thenReturn(false);
        var command = reserve(2_500, "EUR");
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

        assertThatThrownBy(() -> service.reserve(reserve(2_500, "EUR"))).isInstanceOf(QueryTimeoutException.class);
        verifyNoInteractions(replies);
    }

    // ---- post ----

    @Test
    @DisplayName("B1 a posting debits the transit account and credits the creditor, then replies TransferPosted")
    void postingMovesTheAmountToTheCreditorAndReplies() {
        service.post(post(CREDITOR.id(), 2_500, "EUR"));

        var saved = savedTransaction();
        assertThat(saved.lines())
                .extracting(line -> line.accountId(), line -> line.amount())
                .containsExactlyInAnyOrder(
                        tuple(TRANSIT.id(), Money.of(2_500, "EUR")), tuple(CREDITOR.id(), Money.of(-2_500, "EUR")));
        verify(replies).transferPosted(PAYMENT, saved.id());
        verify(replies, never()).postingRefused(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("B1 an unknown creditor is refused with UNKNOWN_ACCOUNT")
    void unknownCreditorIsRefused() {
        var unknown = AccountId.random();
        when(accounts.findById(unknown)).thenReturn(Optional.empty());

        service.post(post(unknown, 2_500, "EUR"));

        verify(replies)
                .postingRefused(PAYMENT, Refusal.UNKNOWN_ACCOUNT, new AccountNotFoundException(unknown).getMessage());
        verify(replies, never()).transferPosted(anyString(), any());
        verify(transactions, never()).save(any());
    }

    @Test
    @DisplayName("B1 a creditor in another currency is refused with CURRENCY_MISMATCH")
    void creditorInAnotherCurrencyIsRefused() {
        var usdCreditor = Account.open(Currency.getInstance("USD"), AccountType.LIABILITY, OverdraftPolicy.NOT_ALLOWED);
        when(accounts.findById(usdCreditor.id())).thenReturn(Optional.of(usdCreditor));

        service.post(post(usdCreditor.id(), 2_500, "EUR"));

        verify(replies).postingRefused(eq(PAYMENT), eq(Refusal.CURRENCY_MISMATCH), anyString());
        verify(transactions, never()).save(any());
    }

    @Test
    @DisplayName("B1 a transit account short of the amount is not a creditor refusal: it propagates")
    void transitShortOfTheAmountPropagates() {
        when(balances.apply(eq(TRANSIT.id()), any())).thenReturn(false);

        assertThatThrownBy(() -> service.post(post(CREDITOR.id(), 2_500, "EUR")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining(TRANSIT.id().toString());
        verifyNoInteractions(replies);
    }

    @Test
    @DisplayName("B3 a redelivered PostTransfer records nothing and replies nothing")
    void redeliveredPostingChangesNothing() {
        when(inbox.firstDelivery(any())).thenReturn(false);

        service.post(post(CREDITOR.id(), 2_500, "EUR"));

        verifyNoInteractions(transactions, balances, replies);
    }

    // ---- release ----

    @Test
    @DisplayName(
            "B2 a release debits the transit account and credits the debtor, then replies FundsReleased with the reason")
    void releaseMovesTheAmountBackToTheDebtorAndReplies() {
        service.release(release(2_500, "EUR"));

        var saved = savedTransaction();
        assertThat(saved.lines())
                .extracting(line -> line.accountId(), line -> line.amount())
                .containsExactlyInAnyOrder(
                        tuple(TRANSIT.id(), Money.of(2_500, "EUR")), tuple(DEBTOR.id(), Money.of(-2_500, "EUR")));
        verify(replies).fundsReleased(PAYMENT, saved.id(), "creditor account closed");
    }

    @Test
    @DisplayName("B2 a release is never refused: a failure propagates so the message is retried")
    void releaseIsNeverRefused() {
        var unknown = AccountId.random();
        when(accounts.findById(unknown)).thenReturn(Optional.empty());
        var command =
                new ReleaseFundsCommand(UUID.randomUUID(), PAYMENT, unknown, Money.of(2_500, "EUR"), "creditor closed");

        assertThatThrownBy(() -> service.release(command)).isInstanceOf(AccountNotFoundException.class);
        verifyNoInteractions(replies);
    }

    @Test
    @DisplayName("B3 a redelivered ReleaseFunds records nothing and replies nothing")
    void redeliveredReleaseChangesNothing() {
        when(inbox.firstDelivery(any())).thenReturn(false);

        service.release(release(2_500, "EUR"));

        verifyNoInteractions(transactions, balances, replies);
    }

    // ---- commands ----

    @Test
    void amountsMustBePositive() {
        assertThatThrownBy(() -> reserve(0, "EUR"))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("Amount to reserve must be positive, got " + Money.of(0, "EUR"));
        assertThatThrownBy(() -> post(CREDITOR.id(), 0, "EUR"))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("Amount to post must be positive, got " + Money.of(0, "EUR"));
        assertThatThrownBy(() -> release(-1, "EUR"))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("Amount to release must be positive, got " + Money.of(-1, "EUR"));
    }

    private Transaction savedTransaction() {
        var saved = ArgumentCaptor.forClass(Transaction.class);
        verify(transactions).save(saved.capture());
        return saved.getValue();
    }

    private static ReserveFundsCommand reserve(long amount, String currency) {
        return new ReserveFundsCommand(UUID.randomUUID(), PAYMENT, DEBTOR.id(), Money.of(amount, currency));
    }

    private static PostTransferCommand post(AccountId creditor, long amount, String currency) {
        return new PostTransferCommand(UUID.randomUUID(), PAYMENT, creditor, Money.of(amount, currency));
    }

    private static ReleaseFundsCommand release(long amount, String currency) {
        return new ReleaseFundsCommand(
                UUID.randomUUID(), PAYMENT, DEBTOR.id(), Money.of(amount, currency), "creditor account closed");
    }
}
