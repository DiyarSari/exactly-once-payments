package com.diyarsari.transfer.domain;

/** Business reasons a well-formed transfer request can be declined. Declined transfers are stored too. */
public enum RejectionReason {
    INSUFFICIENT_FUNDS,
    CURRENCY_MISMATCH
}
