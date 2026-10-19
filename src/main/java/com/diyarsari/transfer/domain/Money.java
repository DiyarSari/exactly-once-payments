package com.diyarsari.transfer.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money helpers. Amounts are always {@link BigDecimal} with scale 2; a double
 * cannot represent 0.10 exactly and would silently lose kuruş.
 */
public final class Money {

    public static final int SCALE = 2;

    private Money() {
    }

    /** Normalizes to scale 2. Throws if that would require rounding. */
    public static BigDecimal of(BigDecimal amount) {
        return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal of(String amount) {
        return of(new BigDecimal(amount));
    }
}
