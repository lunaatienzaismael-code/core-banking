package com.ledgerflow.ledger.adapter.in.web;

import com.ledgerflow.ledger.adapter.in.web.model.RecordTransactionRequestDto;
import com.ledgerflow.ledger.adapter.in.web.model.SideDto;
import com.ledgerflow.ledger.adapter.in.web.model.TransactionDto;
import com.ledgerflow.ledger.adapter.in.web.model.TransactionLineDto;
import com.ledgerflow.ledger.adapter.in.web.model.TransactionLineRequestDto;
import com.ledgerflow.ledger.application.port.in.RecordTransactionCommand;
import com.ledgerflow.ledger.application.port.in.TransactionView;
import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.Money;
import com.ledgerflow.ledger.domain.Side;
import java.time.ZoneOffset;

/**
 * Explicit mapping between transaction DTOs and use case types (ADR 0002 rule 7). The contract carries a positive
 * amount plus a direction; the ledger stores a signed amount (spec "Sign convention": debit +, credit −).
 */
final class TransactionWebMapper {

    private TransactionWebMapper() {}

    static RecordTransactionCommand toCommand(RecordTransactionRequestDto request) {
        return new RecordTransactionCommand(request.getLines().stream()
                .map(TransactionWebMapper::toCommandLine)
                .toList());
    }

    static TransactionDto toDto(TransactionView view) {
        return new TransactionDto(
                view.id().value(),
                view.occurredAt().atOffset(ZoneOffset.UTC),
                view.lines().stream().map(TransactionWebMapper::toDto).toList());
    }

    private static RecordTransactionCommand.Line toCommandLine(TransactionLineRequestDto line) {
        var amount = Money.of(line.getAmount().getAmount(), line.getAmount().getCurrency());
        var signed = switch (line.getDirection()) {
            case DEBIT -> amount;
            case CREDIT -> amount.negate();
        };
        return new RecordTransactionCommand.Line(new AccountId(line.getAccountId()), signed);
    }

    private static TransactionLineDto toDto(TransactionView.Line line) {
        return new TransactionLineDto(
                line.postingId().value(),
                line.accountId().value(),
                AccountWebMapper.toDto(line.amount()),
                toDto(line.direction()));
    }

    private static SideDto toDto(Side side) {
        return switch (side) {
            case DEBIT -> SideDto.DEBIT;
            case CREDIT -> SideDto.CREDIT;
        };
    }
}
