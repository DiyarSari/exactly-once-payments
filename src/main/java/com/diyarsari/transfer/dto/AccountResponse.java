package com.diyarsari.transfer.dto;

import com.diyarsari.transfer.domain.Account;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        Long id,
        String accountNumber,
        String ownerName,
        String currency,
        BigDecimal balance,
        Instant createdAt
) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getAccountNumber(), account.getOwnerName(),
                account.getCurrency(), account.getBalance(), account.getCreatedAt());
    }
}
