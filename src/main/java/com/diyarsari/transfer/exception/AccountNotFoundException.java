package com.diyarsari.transfer.exception;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(Long id) {
        super("Account " + id + " does not exist");
    }
}
