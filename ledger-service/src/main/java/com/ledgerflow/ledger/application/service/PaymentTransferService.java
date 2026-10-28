package com.ledgerflow.ledger.application.service;

import com.ledgerflow.ledger.application.port.in.PostTransferCommand;
import com.ledgerflow.ledger.application.port.in.PostTransferUseCase;
import com.ledgerflow.ledger.application.port.in.RecordTransactionCommand;
import com.ledgerflow.ledger.application.port.in.RecordTransactionUseCase;
import com.ledgerflow.ledger.application.port.in.ReleaseFundsCommand;
import com.ledgerflow.ledger.application.port.in.ReleaseFundsUseCase;
import com.ledgerflow.ledger.application.port.in.ReserveFundsCommand;
import com.ledgerflow.ledger.application.port.in.ReserveFundsUseCase;
import com.ledgerflow.ledger.application.port.out.AccountRepository;
import com.ledgerflow.ledger.application.port.out.MessageInbox;
import com.ledgerflow.ledger.application.port.out.TransferReplies;
import com.ledgerflow.ledger.application.port.out.TransferReplies.Refusal;
import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.AccountNotFoundException;
import com.ledgerflow.ledger.domain.CurrencyMismatchException;
import com.ledgerflow.ledger.domain.DomainException;
import com.ledgerflow.ledger.domain.InsufficientFundsException;
import com.ledgerflow.ledger.domain.Money;
import com.ledgerflow.ledger.domain.TransactionId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Moves a payment's money through the payments-in-transit account (ADR 0012): reserve (debtor → transit), post
 * (transit → creditor) and release (transit → debtor), each through the UC2 write path, at most once per message
 * (AC4.2).
 *
 * <p>A refusal is found while recording, after postings or counters may already be written, so it cannot share their
 * transaction: the attempt rolls back whole, and a second transaction records the message and the refusal. Hence
 * {@link TransactionOperations} rather than {@code @Transactional}.
 */
@Service
class PaymentTransferService implements ReserveFundsUseCase, PostTransferUseCase, ReleaseFundsUseCase {

    private static final Set<Refusal> ANY_REFUSAL = EnumSet.allOf(Refusal.class);
    // The transit account holds this payment's reservation, so only the creditor can make a posting fail.
    private static final Set<Refusal> CREDITOR_REFUSALS =
            EnumSet.of(Refusal.UNKNOWN_ACCOUNT, Refusal.CURRENCY_MISMATCH);
    // Releasing a reservation reverses postings on accounts that already took them: it is never refused.
    private static final Set<Refusal> NO_REFUSAL = EnumSet.noneOf(Refusal.class);

    private final MessageInbox inbox;
    private final AccountRepository accounts;
    private final RecordTransactionUseCase recordTransaction;
    private final TransferReplies replies;
    private final TransactionOperations transactions;

    PaymentTransferService(
            MessageInbox inbox,
            AccountRepository accounts,
            RecordTransactionUseCase recordTransaction,
            TransferReplies replies,
            TransactionOperations transactions) {
        this.inbox = inbox;
        this.accounts = accounts;
        this.recordTransaction = recordTransaction;
        this.replies = replies;
        this.transactions = transactions;
    }

    @Override
    public void reserve(ReserveFundsCommand command) {
        var paymentId = command.paymentId();
        var transit = AccountId.paymentsInTransit(command.amount().currency());
        move(
                command.messageId(),
                new Movement(command.debtor(), transit, command.amount()),
                transaction -> replies.fundsReserved(paymentId, transaction),
                ANY_REFUSAL,
                (reason, detail) -> replies.reservationRefused(paymentId, reason, detail));
    }

    @Override
    public void post(PostTransferCommand command) {
        var paymentId = command.paymentId();
        var transit = AccountId.paymentsInTransit(command.amount().currency());
        move(
                command.messageId(),
                new Movement(transit, command.creditor(), command.amount()),
                transaction -> replies.transferPosted(paymentId, transaction),
                CREDITOR_REFUSALS,
                (reason, detail) -> replies.postingRefused(paymentId, reason, detail));
    }

    @Override
    public void release(ReleaseFundsCommand command) {
        var paymentId = command.paymentId();
        var transit = AccountId.paymentsInTransit(command.amount().currency());
        move(
                command.messageId(),
                new Movement(transit, command.debtor(), command.amount()),
                transaction -> replies.fundsReleased(paymentId, transaction, command.reason()),
                NO_REFUSAL,
                (reason, detail) -> {
                    throw new IllegalStateException("A release is never refused");
                });
    }

    /** {@code amount} is debited to {@code debited} (+) and credited to {@code credited} (−). */
    private record Movement(AccountId debited, AccountId credited, Money amount) {}

    private void move(
            UUID messageId,
            Movement movement,
            Consumer<TransactionId> onMoved,
            Set<Refusal> refusable,
            BiConsumer<Refusal, String> onRefused) {
        try {
            transactions.executeWithoutResult(status -> {
                if (!inbox.firstDelivery(messageId)) {
                    return;
                }
                // Created on first use in each currency; a no-op afterwards (ADR 0012).
                accounts.saveIfAbsent(
                        Account.paymentsInTransit(movement.amount().currency()));
                var recorded = recordTransaction.record(new RecordTransactionCommand(List.of(
                        new RecordTransactionCommand.Line(movement.debited(), movement.amount()),
                        new RecordTransactionCommand.Line(
                                movement.credited(), movement.amount().negate()))));
                onMoved.accept(recorded.id());
            });
        } catch (DomainException failure) {
            var reason = refusalOf(failure).filter(refusable::contains).orElseThrow(() -> failure);
            transactions.executeWithoutResult(status -> {
                if (inbox.firstDelivery(messageId)) {
                    onRefused.accept(reason, failure.getMessage());
                }
            });
        }
    }

    private static Optional<Refusal> refusalOf(DomainException failure) {
        return switch (failure) {
            case InsufficientFundsException ignored -> Optional.of(Refusal.INSUFFICIENT_FUNDS);
            case AccountNotFoundException ignored -> Optional.of(Refusal.UNKNOWN_ACCOUNT);
            case CurrencyMismatchException ignored -> Optional.of(Refusal.CURRENCY_MISMATCH);
            default -> Optional.empty();
        };
    }
}
