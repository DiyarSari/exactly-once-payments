package com.diyarsari.transfer.dto;

import com.diyarsari.transfer.domain.EntryType;
import com.diyarsari.transfer.domain.LedgerEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(
        Long id,
        UUID transferId,
        EntryType entryType,
        BigDecimal amount,
        String currency,
        BigDecimal balanceAfter,
        Instant createdAt
) {

    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(entry.getId(), entry.getTransfer().getId(), entry.getEntryType(),
                entry.getAmount(), entry.getCurrency(), entry.getBalanceAfter(), entry.getCreatedAt());
    }
}
