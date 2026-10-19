package com.diyarsari.transfer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 100, updatable = false)
    private String idempotencyKey;

    /** SHA-256 of the canonical request, to detect a key being reused for a different request. */
    @Column(nullable = false, length = 64, updatable = false)
    private String requestHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_account_id", nullable = false, updatable = false)
    private Account fromAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_account_id", nullable = false, updatable = false)
    private Account toAccount;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(length = 140, updatable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransferStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private RejectionReason failureReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Transfer() {
    }

    private Transfer(String idempotencyKey, String requestHash, Account from, Account to,
                     BigDecimal amount, String description, TransferStatus status, RejectionReason failureReason) {
        this.id = UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.fromAccount = from;
        this.toAccount = to;
        this.amount = Money.of(amount);
        this.currency = from.getCurrency();
        this.description = description;
        this.status = status;
        this.failureReason = failureReason;
        this.createdAt = Instant.now();
    }

    public static Transfer completed(String idempotencyKey, String requestHash, Account from, Account to,
                                     BigDecimal amount, String description) {
        return new Transfer(idempotencyKey, requestHash, from, to, amount, description, TransferStatus.COMPLETED, null);
    }

    public static Transfer rejected(String idempotencyKey, String requestHash, Account from, Account to,
                                    BigDecimal amount, String description, RejectionReason reason) {
        return new Transfer(idempotencyKey, requestHash, from, to, amount, description, TransferStatus.REJECTED, reason);
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Account getFromAccount() {
        return fromAccount;
    }

    public Account getToAccount() {
        return toAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public RejectionReason getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
