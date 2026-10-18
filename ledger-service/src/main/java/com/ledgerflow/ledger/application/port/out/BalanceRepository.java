package com.ledgerflow.ledger.application.port.out;

import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.Money;

/** The account's materialised posting counters (ADR 0009), which always equal the sums of its postings. */
public interface BalanceRepository {

    /** Signed net of the account's postings (debits posted − credits posted) in its currency; zero when it has none. */
    Money signedNetOf(Account account);

    /**
     * Adds a signed posting amount to the account's counters with one atomic SQL update (ADR 0009): a positive amount
     * to its debits posted, a negative one to its credits posted. For a {@code NOT_ALLOWED} account the same statement
     * refuses an amount that would take its normal-side balance below zero. Must run in the transaction that saves the
     * postings.
     *
     * <p>Precondition: the account exists and was loaded in the same transaction; a missing account also yields
     * {@code false}, which the caller would report as a refusal.
     *
     * @return {@code false} when the overdraft guard refused the amount; nothing was changed
     */
    boolean apply(AccountId account, Money signedAmount);
}
