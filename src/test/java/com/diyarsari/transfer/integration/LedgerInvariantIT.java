package com.diyarsari.transfer.integration;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs after every other integration test class (see junit-platform.properties) and checks the
 * accounting invariants over everything the suite has written to the shared database.
 */
@Order(Integer.MAX_VALUE)
class LedgerInvariantIT extends AbstractIntegrationTest {

    @Test
    void ledgerSumsToZeroAfterAllTests() {
        Integer completed = jdbc.queryForObject("SELECT COUNT(*) FROM transfers WHERE status = 'COMPLETED'",
                Integer.class);
        assertThat(completed).as("the suite should have produced transfers to check").isPositive();

        LedgerInvariants.assertHold(jdbc);
    }
}
