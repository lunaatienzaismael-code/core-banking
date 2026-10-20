package com.ledgerflow.payment.adapter.in.web;

import com.ledgerflow.payment.adapter.in.web.model.InitiatePaymentRequestDto;
import com.ledgerflow.payment.adapter.in.web.model.PaymentDto;
import com.ledgerflow.payment.adapter.in.web.model.PaymentStatusDto;
import com.ledgerflow.payment.adapter.in.web.model.PositiveMoneyDto;
import com.ledgerflow.payment.application.port.in.InitiatePaymentCommand;
import com.ledgerflow.payment.application.port.in.PaymentView;
import com.ledgerflow.payment.domain.AccountId;
import com.ledgerflow.payment.domain.Money;
import com.ledgerflow.payment.domain.PaymentStatus;
import java.time.ZoneOffset;

/**
 * Explicit mapping between contract DTOs and use case types (ADR 0002 rule 7). Exhaustive switches fail to compile
 * when either side gains a value.
 */
final class PaymentWebMapper {

    private PaymentWebMapper() {}

    static InitiatePaymentCommand toCommand(InitiatePaymentRequestDto request) {
        return new InitiatePaymentCommand(
                new AccountId(request.getDebtorAccountId()),
                new AccountId(request.getCreditorAccountId()),
                Money.of(request.getAmount().getAmount(), request.getAmount().getCurrency()));
    }

    static PaymentDto toDto(PaymentView view) {
        var dto = new PaymentDto(
                view.id().value(),
                toDto(view.status()),
                view.debtor().value(),
                view.creditor().value(),
                new PositiveMoneyDto(
                        view.amount().amount(), view.amount().currency().getCurrencyCode()),
                view.initiatedAt().atOffset(ZoneOffset.UTC));
        view.reason().ifPresent(dto::setReason);
        return dto;
    }

    private static PaymentStatusDto toDto(PaymentStatus status) {
        return switch (status) {
            case INITIATED -> PaymentStatusDto.INITIATED;
            case RISK_APPROVED -> PaymentStatusDto.RISK_APPROVED;
            case FUNDS_RESERVED -> PaymentStatusDto.FUNDS_RESERVED;
            case COMPLETED -> PaymentStatusDto.COMPLETED;
            case REJECTED -> PaymentStatusDto.REJECTED;
            case FAILED -> PaymentStatusDto.FAILED;
        };
    }
}
