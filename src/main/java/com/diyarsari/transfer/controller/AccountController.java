package com.diyarsari.transfer.controller;

import com.diyarsari.transfer.dto.AccountResponse;
import com.diyarsari.transfer.dto.CreateAccountRequest;
import com.diyarsari.transfer.dto.LedgerEntryResponse;
import com.diyarsari.transfer.dto.PageResponse;
import com.diyarsari.transfer.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(name = "Accounts")
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "Open an account with an initial balance")
    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse account = accountService.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(account.id()).toUri();
        return ResponseEntity.created(location).body(account);
    }

    @Operation(summary = "Get an account and its current balance")
    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable Long id) {
        return accountService.get(id);
    }

    @Operation(summary = "List an account's ledger entries, newest first")
    @GetMapping("/{id}/ledger")
    public PageResponse<LedgerEntryResponse> ledger(@PathVariable Long id,
                                                    @RequestParam(defaultValue = "0") @Min(0) int page,
                                                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return accountService.ledger(id, page, size);
    }
}
