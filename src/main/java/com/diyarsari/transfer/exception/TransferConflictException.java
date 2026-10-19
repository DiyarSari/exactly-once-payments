package com.diyarsari.transfer.exception;

/** Optimistic-lock retries were exhausted. Safe for the client to retry with the same Idempotency-Key. */
public class TransferConflictException extends RuntimeException {

    public TransferConflictException(int attempts, Throwable cause) {
        super("Transfer could not be completed after " + attempts
                + " attempts due to concurrent updates; retry with the same Idempotency-Key", cause);
    }
}
