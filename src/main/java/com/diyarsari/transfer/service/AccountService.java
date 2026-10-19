package com.diyarsari.transfer.service;

import com.diyarsari.transfer.domain.Account;
import com.diyarsari.transfer.dto.AccountResponse;
import com.diyarsari.transfer.dto.CreateAccountRequest;
import com.diyarsari.transfer.dto.LedgerEntryResponse;
import com.diyarsari.transfer.dto.PageResponse;
import com.diyarsari.transfer.exception.AccountNotFoundException;
import com.diyarsari.transfer.repository.AccountRepository;
import com.diyarsari.transfer.repository.LedgerEntryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final AccountNumberGenerator accountNumbers;

    public AccountService(AccountRepository accounts, LedgerEntryRepository ledger,
                          AccountNumberGenerator accountNumbers) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.accountNumbers = accountNumbers;
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        Account account = new Account(accountNumbers.next(), request.ownerName().strip(),
                request.currencyOrDefault(), request.initialBalance());
        return AccountResponse.from(accounts.save(account));
    }

    @Transactional(readOnly = true)
    public AccountResponse get(Long id) {
        return accounts.findById(id).map(AccountResponse::from).orElseThrow(() -> new AccountNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<LedgerEntryResponse> ledger(Long accountId, int page, int size) {
        if (!accounts.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        return PageResponse.from(ledger.findByAccountIdOrderByIdDesc(accountId, PageRequest.of(page, size)),
                LedgerEntryResponse::from);
    }
}
