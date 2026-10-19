package com.diyarsari.transfer.integration;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Accounting invariants that must hold for the whole database at any point in time. */
final class LedgerInvariants {

    private LedgerInvariants() {
    }

    static void assertHold(JdbcTemplate jdbc) {
        // 1. Double entry: per currency, all debits and credits cancel out.
        List<Map<String, Object>> perCurrency = jdbc.queryForList("""
                SELECT currency, SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE -amount END) AS total
                FROM ledger_entries GROUP BY currency""");
        perCurrency.forEach(row -> assertThat((BigDecimal) row.get("total"))
                .as("ledger sum for %s", row.get("currency"))
                .isEqualByComparingTo(BigDecimal.ZERO));

        // 2. Every COMPLETED transfer has exactly one DEBIT and one CREDIT of its amount; REJECTED ones have none.
        Integer badTransfers = jdbc.queryForObject("""
                SELECT COUNT(*) FROM transfers t
                WHERE (t.status = 'COMPLETED' AND (
                          (SELECT COUNT(*) FROM ledger_entries e WHERE e.transfer_id = t.id) <> 2
                       OR NOT EXISTS (SELECT 1 FROM ledger_entries e WHERE e.transfer_id = t.id
                                      AND e.entry_type = 'DEBIT' AND e.account_id = t.from_account_id AND e.amount = t.amount)
                       OR NOT EXISTS (SELECT 1 FROM ledger_entries e WHERE e.transfer_id = t.id
                                      AND e.entry_type = 'CREDIT' AND e.account_id = t.to_account_id AND e.amount = t.amount)))
                   OR (t.status = 'REJECTED' AND EXISTS (SELECT 1 FROM ledger_entries e WHERE e.transfer_id = t.id))""",
                Integer.class);
        assertThat(badTransfers).as("transfers with inconsistent ledger entries").isZero();

        // 3. Every balance can be reconstructed from its opening balance plus its ledger entries.
        Integer unreconciled = jdbc.queryForObject("""
                SELECT COUNT(*) FROM accounts a
                WHERE a.balance <> a.opening_balance + COALESCE((
                    SELECT SUM(CASE WHEN e.entry_type = 'CREDIT' THEN e.amount ELSE -e.amount END)
                    FROM ledger_entries e WHERE e.account_id = a.id), 0)""", Integer.class);
        assertThat(unreconciled).as("accounts whose balance does not match their ledger").isZero();

        // 4. No account is overdrawn (also enforced by a CHECK constraint).
        Integer negative = jdbc.queryForObject("SELECT COUNT(*) FROM accounts WHERE balance < 0", Integer.class);
        assertThat(negative).as("accounts with negative balance").isZero();
    }
}
