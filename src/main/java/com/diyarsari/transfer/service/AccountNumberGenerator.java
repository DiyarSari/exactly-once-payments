package com.diyarsari.transfer.service;

import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * Generates Turkish-format IBANs (TRkk BBBBB 0 AAAAAAAAAAAAAAAA) with valid ISO 13616
 * mod-97 check digits. The bank code 99999 is fictional.
 */
@Component
public class AccountNumberGenerator {

    static final String BANK_CODE = "99999";
    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);
    // "TR00" moved to the end with letters converted to numbers: T=29, R=27.
    private static final String COUNTRY_SUFFIX = "292700";

    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder account = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            account.append(random.nextInt(10));
        }
        return withCheckDigits(BANK_CODE + "0" + account);
    }

    static String withCheckDigits(String bban) {
        int check = 98 - new BigInteger(bban + COUNTRY_SUFFIX).mod(NINETY_SEVEN).intValue();
        return "TR%02d%s".formatted(check, bban);
    }

    /** True if the IBAN's check digits are valid. */
    static boolean isValid(String iban) {
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder numeric = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            numeric.append(Character.isLetter(c) ? String.valueOf(c - 'A' + 10) : String.valueOf(c));
        }
        return new BigInteger(numeric.toString()).mod(NINETY_SEVEN).intValue() == 1;
    }
}
