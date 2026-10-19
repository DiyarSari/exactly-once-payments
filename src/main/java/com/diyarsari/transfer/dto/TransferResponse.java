package com.diyarsari.transfer.dto;

import com.diyarsari.transfer.domain.RejectionReason;
import com.diyarsari.transfer.domain.Transfer;
import com.diyarsari.transfer.domain.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        String currency,
        String description,
        TransferStatus status,
        RejectionReason failureReason,
        Instant createdAt
) {

    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(transfer.getId(), transfer.getFromAccount().getId(),
                transfer.getToAccount().getId(), transfer.getAmount(), transfer.getCurrency(),
                transfer.getDescription(), transfer.getStatus(), transfer.getFailureReason(),
                transfer.getCreatedAt());
    }
}
