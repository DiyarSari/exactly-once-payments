package com.diyarsari.transfer.service;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountNumberGeneratorTest {

    private final AccountNumberGenerator generator = new AccountNumberGenerator();

    @RepeatedTest(20)
    void generatesValidTurkishIbans() {
        String iban = generator.next();
        assertThat(iban).hasSize(26).startsWith("TR").matches("TR\\d{24}");
        assertThat(iban.substring(4, 9)).isEqualTo(AccountNumberGenerator.BANK_CODE);
        assertThat(AccountNumberGenerator.isValid(iban)).isTrue();
    }

    @Test
    void seededDemoIbansAreValid() {
        assertThat(AccountNumberGenerator.isValid("TR809999900000000000000001")).isTrue();
        assertThat(AccountNumberGenerator.isValid("TR539999900000000000000002")).isTrue();
        assertThat(AccountNumberGenerator.isValid("TR269999900000000000000003")).isTrue();
    }

    @Test
    void detectsATypo() {
        assertThat(AccountNumberGenerator.isValid("TR809999900000000000000002")).isFalse();
    }
}
