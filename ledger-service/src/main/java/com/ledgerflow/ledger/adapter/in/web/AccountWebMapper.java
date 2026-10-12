package com.ledgerflow.ledger.adapter.in.web;

import com.ledgerflow.ledger.adapter.in.web.model.AccountBalanceDto;
import com.ledgerflow.ledger.adapter.in.web.model.AccountDto;
import com.ledgerflow.ledger.adapter.in.web.model.AccountMovementsDto;
import com.ledgerflow.ledger.adapter.in.web.model.AccountTypeDto;
import com.ledgerflow.ledger.adapter.in.web.model.MoneyDto;
import com.ledgerflow.ledger.adapter.in.web.model.MovementDto;
import com.ledgerflow.ledger.adapter.in.web.model.OpenAccountRequestDto;
import com.ledgerflow.ledger.adapter.in.web.model.OverdraftPolicyDto;
import com.ledgerflow.ledger.adapter.in.web.model.SideDto;
import com.ledgerflow.ledger.application.port.in.AccountBalanceView;
import com.ledgerflow.ledger.application.port.in.AccountView;
import com.ledgerflow.ledger.application.port.in.MovementView;
import com.ledgerflow.ledger.application.port.in.OpenAccountCommand;
import com.ledgerflow.ledger.domain.AccountType;
import com.ledgerflow.ledger.domain.Money;
import com.ledgerflow.ledger.domain.OverdraftPolicy;
import com.ledgerflow.ledger.domain.Side;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Explicit mapping between contract DTOs and use case types (ADR 0002 rule 7). Exhaustive switches fail to compile when either side gains a value. */
final class AccountWebMapper {

    private AccountWebMapper() {}

    static OpenAccountCommand toCommand(OpenAccountRequestDto request) {
        return new OpenAccountCommand(
                Money.currencyOf(request.getCurrency()),
                toDomain(request.getType()),
                toDomain(request.getOverdraftPolicy()));
    }

    static AccountDto toDto(AccountView view) {
        return new AccountDto(
                view.id().value(),
                view.currency().getCurrencyCode(),
                toDto(view.type()),
                toDto(view.normalBalanceSide()),
                toDto(view.overdraftPolicy()),
                toDto(view.balance()));
    }

    static AccountBalanceDto toDto(AccountBalanceView view) {
        return new AccountBalanceDto(view.accountId().value(), toDto(view.normalBalanceSide()), toDto(view.balance()));
    }

    static AccountMovementsDto toDto(UUID accountId, List<MovementView> movements) {
        return new AccountMovementsDto(
                accountId, movements.stream().map(AccountWebMapper::toDto).toList());
    }

    static MovementDto toDto(MovementView movement) {
        return new MovementDto(
                movement.postingId().value(),
                movement.transactionId().value(),
                toDto(movement.amount()),
                toDto(movement.direction()),
                movement.occurredAt().atOffset(ZoneOffset.UTC));
    }

    static MoneyDto toDto(Money money) {
        return new MoneyDto(money.amount(), money.currency().getCurrencyCode());
    }

    private static AccountType toDomain(AccountTypeDto type) {
        return switch (type) {
            case ASSET -> AccountType.ASSET;
            case LIABILITY -> AccountType.LIABILITY;
            case EQUITY -> AccountType.EQUITY;
            case INCOME -> AccountType.INCOME;
            case EXPENSE -> AccountType.EXPENSE;
        };
    }

    private static OverdraftPolicy toDomain(OverdraftPolicyDto policy) {
        return switch (policy) {
            case ALLOWED -> OverdraftPolicy.ALLOWED;
            case NOT_ALLOWED -> OverdraftPolicy.NOT_ALLOWED;
        };
    }

    private static AccountTypeDto toDto(AccountType type) {
        return switch (type) {
            case ASSET -> AccountTypeDto.ASSET;
            case LIABILITY -> AccountTypeDto.LIABILITY;
            case EQUITY -> AccountTypeDto.EQUITY;
            case INCOME -> AccountTypeDto.INCOME;
            case EXPENSE -> AccountTypeDto.EXPENSE;
        };
    }

    private static OverdraftPolicyDto toDto(OverdraftPolicy policy) {
        return switch (policy) {
            case ALLOWED -> OverdraftPolicyDto.ALLOWED;
            case NOT_ALLOWED -> OverdraftPolicyDto.NOT_ALLOWED;
        };
    }

    private static SideDto toDto(Side side) {
        return switch (side) {
            case DEBIT -> SideDto.DEBIT;
            case CREDIT -> SideDto.CREDIT;
        };
    }
}
