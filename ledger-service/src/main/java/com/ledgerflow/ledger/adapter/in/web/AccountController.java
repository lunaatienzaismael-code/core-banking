package com.ledgerflow.ledger.adapter.in.web;

import com.ledgerflow.ledger.adapter.in.web.api.AccountsApi;
import com.ledgerflow.ledger.adapter.in.web.model.AccountDto;
import com.ledgerflow.ledger.adapter.in.web.model.OpenAccountRequestDto;
import com.ledgerflow.ledger.application.port.in.OpenAccountUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Implements the generated contract interface (ADR 0008); routes, payloads and validation come from the contract. */
@RestController
class AccountController implements AccountsApi {

    private final OpenAccountUseCase openAccount;

    AccountController(OpenAccountUseCase openAccount) {
        this.openAccount = openAccount;
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
}
