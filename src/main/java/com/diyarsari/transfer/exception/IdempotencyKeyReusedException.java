package com.diyarsari.transfer.exception;

/** The Idempotency-Key was already used for a request with a different body. */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException(String key) {
        super("Idempotency-Key '" + key + "' was already used with a different request body");
    }
}
