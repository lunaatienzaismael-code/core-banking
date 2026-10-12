package com.ledgerflow.ledger.adapter.in.web;

import com.ledgerflow.ledger.adapter.in.web.api.AccountsApi;
import com.ledgerflow.ledger.adapter.in.web.model.AccountBalanceDto;
import com.ledgerflow.ledger.adapter.in.web.model.AccountDto;
import com.ledgerflow.ledger.adapter.in.web.model.AccountMovementsDto;
import com.ledgerflow.ledger.adapter.in.web.model.OpenAccountRequestDto;
import com.ledgerflow.ledger.application.port.in.GetAccountBalanceUseCase;
import com.ledgerflow.ledger.application.port.in.GetAccountMovementsUseCase;
import com.ledgerflow.ledger.application.port.in.OpenAccountUseCase;
import com.ledgerflow.ledger.domain.AccountId;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Implements the generated contract interface (ADR 0008); routes, payloads and validation come from the contract. */
@RestController
class AccountController implements AccountsApi {

    private final OpenAccountUseCase openAccount;
    private final GetAccountBalanceUseCase getAccountBalance;
    private final GetAccountMovementsUseCase getAccountMovements;

    AccountController(
            OpenAccountUseCase openAccount,
            GetAccountBalanceUseCase getAccountBalance,
            GetAccountMovementsUseCase getAccountMovements) {
        this.openAccount = openAccount;
        this.getAccountBalance = getAccountBalance;
        this.getAccountMovements = getAccountMovements;
    }

    @Override
    public ResponseEntity<AccountDto> openAccount(OpenAccountRequestDto request) {
        var account = openAccount.open(AccountWebMapper.toCommand(request));
        var location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(account.id().value())
                .toUri();
        return ResponseEntity.created(location).body(AccountWebMapper.toDto(account));
    }

    @Override
    public ResponseEntity<AccountBalanceDto> getAccountBalance(UUID accountId) {
        var balance = getAccountBalance.balanceOf(new AccountId(accountId));
        return ResponseEntity.ok(AccountWebMapper.toDto(balance));
    }

    @Override
    public ResponseEntity<AccountMovementsDto> getAccountMovements(UUID accountId) {
        var movements = getAccountMovements.movementsOf(new AccountId(accountId));
        return ResponseEntity.ok(AccountWebMapper.toDto(accountId, movements));
    }
}
