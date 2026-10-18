package com.ledgerflow.ledger.application.service;

import com.ledgerflow.ledger.application.port.in.RecordTransactionCommand;
import com.ledgerflow.ledger.application.port.in.RecordTransactionUseCase;
import com.ledgerflow.ledger.application.port.in.TransactionView;
import com.ledgerflow.ledger.application.port.out.AccountRepository;
import com.ledgerflow.ledger.application.port.out.BalanceRepository;
import com.ledgerflow.ledger.application.port.out.TransactionRepository;
import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.AccountNotFoundException;
import com.ledgerflow.ledger.domain.CurrencyMismatchException;
import com.ledgerflow.ledger.domain.Money;
import com.ledgerflow.ledger.domain.PostingId;
import com.ledgerflow.ledger.domain.PostingLine;
import com.ledgerflow.ledger.domain.Transaction;
import com.ledgerflow.ledger.domain.TransactionId;
import java.time.Clock;
import java.util.Comparator;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RecordTransactionService implements RecordTransactionUseCase {

    /** Any fixed order works as long as every writer uses it; it keeps two transactions from deadlocking (ADR 0009). */
    private static final Comparator<Map.Entry<AccountId, Money>> ACCOUNT_ORDER =
            Map.Entry.comparingByKey(Comparator.comparing(AccountId::value));

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final BalanceRepository balances;
    private final Clock clock;

    RecordTransactionService(
            AccountRepository accounts, TransactionRepository transactions, BalanceRepository balances, Clock clock) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.balances = balances;
        this.clock = clock;
    }

    @Override
    @Transactional
    public TransactionView record(RecordTransactionCommand command) {
        var id = TransactionId.random();
        var lines = command.lines().stream()
                .map(line -> new PostingLine(PostingId.random(), id, line.accountId(), line.amount()))
                .toList();
        // The domain rejects unbalanced, mixed-currency and duplicate-account lines before anything is read or written.
        var transaction = new Transaction(id, clock.instant(), lines);
        for (var line : transaction.lines()) {
            requireAccountInCurrencyOf(line);
        }
        transactions.save(transaction);
        transaction.amountsByAccount().entrySet().stream()
                .sorted(ACCOUNT_ORDER)
                .forEach(entry -> balances.apply(entry.getKey(), entry.getValue()));
        return toView(transaction);
    }

    private void requireAccountInCurrencyOf(PostingLine line) {
        var account =
                accounts.findById(line.accountId()).orElseThrow(() -> new AccountNotFoundException(line.accountId()));
        var currency = line.amount().currency();
        if (!account.currency().equals(currency)) {
            throw new CurrencyMismatchException(account.currency(), currency);
        }
    }

    private static TransactionView toView(Transaction transaction) {
        var lines = transaction.lines().stream()
                .map(line -> new TransactionView.Line(line.id(), line.accountId(), line.magnitude(), line.side()))
                .toList();
        return new TransactionView(transaction.id(), transaction.occurredAt(), lines);
    }
}
