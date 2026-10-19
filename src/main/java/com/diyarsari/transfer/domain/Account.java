package com.diyarsari.transfer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 34)
    private String accountNumber;

    @Column(nullable = false, length = 100)
    private String ownerName;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    /** Balance the account was opened with; used to reconcile balance against the ledger. */
    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal openingBalance;

    /** Optimistic lock: every UPDATE checks and bumps this, so a stale write fails instead of overwriting. */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
    }

    public Account(String accountNumber, String ownerName, String currency, BigDecimal openingBalance) {
        Objects.requireNonNull(openingBalance, "openingBalance");
        if (openingBalance.signum() < 0) {
            throw new IllegalArgumentException("Opening balance must not be negative");
        }
        this.accountNumber = Objects.requireNonNull(accountNumber, "accountNumber");
        this.ownerName = Objects.requireNonNull(ownerName, "ownerName");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.balance = Money.of(openingBalance);
        this.openingBalance = this.balance;
        this.createdAt = Timestamps.now();
    }

    public boolean canDebit(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }

    public void debit(BigDecimal amount) {
        requirePositive(amount);
        if (!canDebit(amount)) {
            throw new InsufficientFundsException(id);
        }
        balance = balance.subtract(Money.of(amount));
    }

    public void credit(BigDecimal amount) {
        requirePositive(amount);
        balance = balance.add(Money.of(amount));
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }

    public Long getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
