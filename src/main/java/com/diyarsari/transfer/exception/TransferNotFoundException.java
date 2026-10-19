package com.diyarsari.transfer.exception;

import java.util.UUID;

public class TransferNotFoundException extends RuntimeException {

    public TransferNotFoundException(UUID id) {
        super("Transfer " + id + " does not exist");
    }
}
