package com.ledgerflow.ledger.application.service;

import com.ledgerflow.ledger.application.port.in.RecordTransactionCommand;
import com.ledgerflow.ledger.application.port.in.RecordTransactionUseCase;
import com.ledgerflow.ledger.application.port.in.ReserveFundsCommand;
import com.ledgerflow.ledger.application.port.in.ReserveFundsUseCase;
import com.ledgerflow.ledger.application.port.out.AccountRepository;
import com.ledgerflow.ledger.application.port.out.MessageInbox;
import com.ledgerflow.ledger.application.port.out.ReservationReplies;
import com.ledgerflow.ledger.application.port.out.ReservationReplies.Refusal;
import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.AccountNotFoundException;
import com.ledgerflow.ledger.domain.CurrencyMismatchException;
import com.ledgerflow.ledger.domain.DomainException;
import com.ledgerflow.ledger.domain.InsufficientFundsException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Reserves funds through the UC2 write path (ADR 0012). A refusal is found while recording, after postings or counters
 * may already be written, so it cannot share their transaction: the attempt rolls back whole, and a second
 * transaction records the message and the refusal. Hence {@link TransactionOperations} rather than
 * {@code @Transactional}.
 */
@Service
class ReserveFundsService implements ReserveFundsUseCase {

    private final MessageInbox inbox;
    private final AccountRepository accounts;
    private final RecordTransactionUseCase recordTransaction;
    private final ReservationReplies replies;
    private final TransactionOperations transactions;

    ReserveFundsService(
            MessageInbox inbox,
            AccountRepository accounts,
            RecordTransactionUseCase recordTransaction,
            ReservationReplies replies,
            TransactionOperations transactions) {
        this.inbox = inbox;
        this.accounts = accounts;
        this.recordTransaction = recordTransaction;
        this.replies = replies;
        this.transactions = transactions;
    }

    @Override
    public void reserve(ReserveFundsCommand command) {
        try {
            transactions.executeWithoutResult(status -> reserveOnce(command));
        } catch (InsufficientFundsException refusal) {
            refuse(command, Refusal.INSUFFICIENT_FUNDS, refusal);
        } catch (AccountNotFoundException refusal) {
            refuse(command, Refusal.UNKNOWN_ACCOUNT, refusal);
        } catch (CurrencyMismatchException refusal) {
            refuse(command, Refusal.CURRENCY_MISMATCH, refusal);
        }
    }

    private void reserveOnce(ReserveFundsCommand command) {
        if (!inbox.firstDelivery(command.messageId())) {
            return;
        }
        var transit = Account.paymentsInTransit(command.amount().currency());
        accounts.saveIfAbsent(transit);
        // Debit the debtor (its balance goes down), credit the transit account (reserved money goes up).
        var recorded = recordTransaction.record(new RecordTransactionCommand(List.of(
                new RecordTransactionCommand.Line(command.debtor(), command.amount()),
                new RecordTransactionCommand.Line(transit.id(), command.amount().negate()))));
        replies.fundsReserved(command.paymentId(), recorded.id());
    }

    private void refuse(ReserveFundsCommand command, Refusal reason, DomainException refusal) {
        transactions.executeWithoutResult(status -> {
            if (inbox.firstDelivery(command.messageId())) {
                replies.reservationRefused(command.paymentId(), reason, refusal.getMessage());
            }
        });
    }
}
