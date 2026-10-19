package com.diyarsari.transfer.domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferRulesTest {

    private static Account account(long id, String currency, String balance) {
        Account account = new Account("TR" + id, "Owner " + id, currency, new BigDecimal(balance));
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    @Test
    void allowsTransferWithinBalance() {
        assertThat(TransferRules.check(account(1, "TRY", "100.00"), account(2, "TRY", "0"), new BigDecimal("99.99")))
                .isEmpty();
    }

    @Test
    void allowsTransferOfExactBalance() {
        assertThat(TransferRules.check(account(1, "TRY", "100.00"), account(2, "TRY", "0"), new BigDecimal("100.00")))
                .isEmpty();
    }

    @Test
    void rejectsInsufficientFunds() {
        assertThat(TransferRules.check(account(1, "TRY", "100.00"), account(2, "TRY", "0"), new BigDecimal("100.01")))
                .contains(RejectionReason.INSUFFICIENT_FUNDS);
    }

    @Test
    void rejectsCurrencyMismatchBeforeCheckingFunds() {
        assertThat(TransferRules.check(account(1, "TRY", "10.00"), account(2, "EUR", "0"), new BigDecimal("500.00")))
                .contains(RejectionReason.CURRENCY_MISMATCH);
    }

    @Test
    void refusesNonPositiveAmount() {
        Account from = account(1, "TRY", "100.00");
        Account to = account(2, "TRY", "0");
        assertThatThrownBy(() -> TransferRules.check(from, to, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TransferRules.check(from, to, new BigDecimal("-5")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesTransferToSameAccount() {
        Account account = account(1, "TRY", "100.00");
        assertThatThrownBy(() -> TransferRules.check(account, account, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
