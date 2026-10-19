package com.diyarsari.transfer.domain;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Business rules that decide whether a transfer between two existing accounts may go through.
 * Pure function with no I/O, so it can be unit tested exhaustively.
 * <p>
 * Shape rules (amount &gt; 0, at most 2 decimals, from != to) are enforced earlier by
 * Bean Validation on the request and result in HTTP 400 without anything being stored.
 */
public final class TransferRules {

    private TransferRules() {
    }

    public static Optional<RejectionReason> check(Account from, Account to, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (from.getId() != null && from.getId().equals(to.getId())) {
            throw new IllegalArgumentException("Source and target account must differ");
        }
        if (!from.getCurrency().equals(to.getCurrency())) {
            return Optional.of(RejectionReason.CURRENCY_MISMATCH);
        }
        if (!from.canDebit(amount)) {
            return Optional.of(RejectionReason.INSUFFICIENT_FUNDS);
        }
        return Optional.empty();
    }
}
