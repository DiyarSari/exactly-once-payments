package com.diyarsari.transfer.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private static Account account(String balance) {
        return new Account("TR000000000000000000000000", "Test Owner", "TRY", new BigDecimal(balance));
    }

    @Test
    void normalizesOpeningBalanceToScaleTwo() {
        Account account = account("100");
        assertThat(account.getBalance()).isEqualByComparingTo("100.00").hasToString("100.00");
        assertThat(account.getOpeningBalance()).isEqualTo(account.getBalance());
    }

    @Test
    void rejectsNegativeOpeningBalance() {
        assertThatThrownBy(() -> account("-0.01")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void debitAndCreditMoveTheBalance() {
        Account account = account("100.00");
        account.debit(new BigDecimal("30.25"));
        account.credit(new BigDecimal("0.10"));
        assertThat(account.getBalance()).isEqualTo(new BigDecimal("69.85"));
    }

    @Test
    void canDebitTheWholeBalance() {
        Account account = account("50.00");
        account.debit(new BigDecimal("50.00"));
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void refusesToOverdraw() {
        Account account = account("50.00");
        assertThatThrownBy(() -> account.debit(new BigDecimal("50.01")))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(account.getBalance()).isEqualTo(new BigDecimal("50.00"));
    }

    @Test
    void refusesNonPositiveAmounts() {
        Account account = account("50.00");
        assertThatThrownBy(() -> account.debit(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.credit(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesAmountsWithMoreThanTwoDecimals() {
        Account account = account("50.00");
        assertThatThrownBy(() -> account.credit(new BigDecimal("0.001"))).isInstanceOf(ArithmeticException.class);
    }
}
