package com.diyarsari.transfer.domain;

import java.math.BigDecimal;

public enum EntryType {
    DEBIT,
    CREDIT;

    /** Signed view of an entry: debits reduce, credits increase. Used for the zero-sum check. */
    public BigDecimal signed(BigDecimal amount) {
        return this == DEBIT ? amount.negate() : amount;
    }
}
