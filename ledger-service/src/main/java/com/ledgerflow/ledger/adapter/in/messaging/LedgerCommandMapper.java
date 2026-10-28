package com.ledgerflow.ledger.adapter.in.messaging;

import com.ledgerflow.ledger.application.port.in.PostTransferCommand;
import com.ledgerflow.ledger.application.port.in.ReleaseFundsCommand;
import com.ledgerflow.ledger.application.port.in.ReserveFundsCommand;
import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.Money;
import com.ledgerflow.messaging.PostTransfer;
import com.ledgerflow.messaging.ReleaseFunds;
import com.ledgerflow.messaging.ReserveFunds;
import java.util.UUID;

/**
 * Avro contract → use case command. A malformed message (an id that is not a UUID, a non-positive amount, an unknown
 * currency) throws here; it is a poison message, which T-031 sends to the dead-letter topic instead of retrying.
 */
final class LedgerCommandMapper {

    private LedgerCommandMapper() {}

    static ReserveFundsCommand toCommand(UUID messageId, ReserveFunds message) {
        return new ReserveFundsCommand(
                messageId,
                message.getPaymentId(),
                new AccountId(UUID.fromString(message.getDebtorAccountId())),
                Money.of(message.getAmountMinorUnits(), message.getCurrency()));
    }

    static PostTransferCommand toCommand(UUID messageId, PostTransfer message) {
        return new PostTransferCommand(
                messageId,
                message.getPaymentId(),
                new AccountId(UUID.fromString(message.getCreditorAccountId())),
                Money.of(message.getAmountMinorUnits(), message.getCurrency()));
    }

    static ReleaseFundsCommand toCommand(UUID messageId, ReleaseFunds message) {
        return new ReleaseFundsCommand(
                messageId,
                message.getPaymentId(),
                new AccountId(UUID.fromString(message.getDebtorAccountId())),
                Money.of(message.getAmountMinorUnits(), message.getCurrency()),
                message.getReason());
    }
}
