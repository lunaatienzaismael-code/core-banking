package com.ledgerflow.ledger.domain;

import java.util.Collection;
import java.util.Currency;
import java.util.Objects;

/**
 * A ledger account in a single currency. It holds no balance: the balance is always derived from its postings
 * (ADR 0004) and reported from the account's normal balance side (spec "Normal balance").
 */
public record Account(AccountId id, Currency currency, AccountType type, OverdraftPolicy overdraftPolicy) {

    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(overdraftPolicy, "overdraftPolicy");
    }

    public static Account open(Currency currency, AccountType type, OverdraftPolicy overdraftPolicy) {
        return new Account(AccountId.random(), currency, type, overdraftPolicy);
    }

    public Side normalSide() {
        return type.normalSide();
    }

    /**
     * Balance from the normal side, given the signed net of this account's postings (Σ debits − Σ credits).
     * Debit-normal accounts report the net as is; credit-normal accounts report its negation.
     */
    public Money balanceFromNet(Money signedNet) {
        Objects.requireNonNull(signedNet, "signedNet");
        if (!currency.equals(signedNet.currency())) {
            throw new CurrencyMismatchException(currency, signedNet.currency());
        }
        return normalSide() == Side.DEBIT ? signedNet : signedNet.negate();
    }

    /** Reference implementation of the derived balance: sums this account's posting lines in memory. */
    public Money balanceOf(Collection<PostingLine> lines) {
        var net = Money.zero(currency);
        for (var line : lines) {
            if (!id.equals(line.accountId())) {
                throw new InvalidPostingException(
                        "Posting " + line.id() + " belongs to account " + line.accountId() + ", not " + id);
            }
            net = net.plus(line.amount());
        }
        return balanceFromNet(net);
    }
}
