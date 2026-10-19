package com.diyarsari.transfer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** One side of a double-entry booking. Immutable once written. */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false, updatable = false)
    private Transfer transfer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6, updatable = false)
    private EntryType entryType;

    /** Always positive; the direction is carried by {@link #entryType}. */
    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal balanceAfter;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    private LedgerEntry(Transfer transfer, Account account, EntryType entryType) {
        this.transfer = transfer;
        this.account = account;
        this.entryType = entryType;
        this.amount = transfer.getAmount();
        this.currency = transfer.getCurrency();
        this.balanceAfter = account.getBalance();
        this.createdAt = transfer.getCreatedAt();
    }

    /** Call after the balances have been moved so {@code balanceAfter} is correct. */
    public static LedgerEntry debit(Transfer transfer) {
        return new LedgerEntry(transfer, transfer.getFromAccount(), EntryType.DEBIT);
    }

    public static LedgerEntry credit(Transfer transfer) {
        return new LedgerEntry(transfer, transfer.getToAccount(), EntryType.CREDIT);
    }

    public Long getId() {
        return id;
    }

    public Transfer getTransfer() {
        return transfer;
    }

    public Account getAccount() {
        return account;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
