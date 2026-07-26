package com.balanced.ledger.web;

import com.balanced.ledger.service.AccountService;
import com.balanced.ledger.service.LedgerService;
import com.balanced.ledger.web.dto.AccountResponse;
import com.balanced.ledger.web.dto.BalanceResponse;
import com.balanced.ledger.web.dto.CreateAccountRequest;
import com.balanced.ledger.web.dto.JournalEntryResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;
    private final LedgerService ledgerService;

    public AccountController(AccountService accountService, LedgerService ledgerService) {
        this.accountService = accountService;
        this.ledgerService = ledgerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        return accountService.create(request);
    }

    @GetMapping
    public List<AccountResponse> list() {
        return accountService.list();
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable UUID id) {
        return accountService.get(id);
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse balance(@PathVariable UUID id) {
        return accountService.balance(id);
    }

    @GetMapping("/{id}/transactions")
    public List<JournalEntryResponse> transactions(@PathVariable UUID id) {
        return ledgerService.listForAccount(id);
    }

    @PostMapping("/{id}/close")
    public AccountResponse close(@PathVariable UUID id) {
        return accountService.close(id);
    }
}
