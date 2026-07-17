package dev.ledger.transfer;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "transfers")
public class Transfer {

    public enum Status {PENDING, COMPLETED, FAILED, REVERSED}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String idempotencyKey;

    private Long customerId;

    private Long fromAccountId;

    private Long toAccountId;

    private long amountCents;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    private String failureReason;

    private Instant createdAt = Instant.now();

    private Instant completedAt;

    protected Transfer() {
    }

    public Transfer(String idempotencyKey, Long customerId, Long fromAccountId, Long toAccountId, long amountCents) {
        this.idempotencyKey = idempotencyKey;
        this.customerId = customerId;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amountCents = amountCents;
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Long getFromAccountId() {
        return fromAccountId;
    }

    public Long getToAccountId() {
        return toAccountId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public Status getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void complete() {
        this.status = Status.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void fail(String reason) {
        this.status = Status.FAILED;
        this.failureReason = reason;
        this.completedAt = Instant.now();
    }
}
