package com.diyarsari.transfer.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

final class Timestamps {

    private Timestamps() {
    }

    /**
     * Current time at PostgreSQL's precision (microseconds). Linux clocks return nanoseconds; without
     * truncation the first response would differ from a later idempotent replay read back from the database.
     */
    static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
