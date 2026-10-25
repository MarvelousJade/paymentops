package dev.ledger.payment;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_refunds")
public class PaymentRefund {

    public enum Status {REQUESTED, COMPLETED, REJECTED}

    @Id
    private UUID id;
    private UUID paymentId;
    private String idempotencyKey;
    private long amountCents;
    @Enumerated(EnumType.STRING)
    private Status status;
    private String failureReason;
    private Instant createdAt;
    private Instant completedAt;

    protected PaymentRefund() {
    }

    public PaymentRefund(UUID paymentId, String idempotencyKey, long amountCents) {
        this.id = UUID.randomUUID();
        this.paymentId = paymentId;
        this.idempotencyKey = idempotencyKey;
        this.amountCents = amountCents;
        this.status = Status.REQUESTED;
        this.createdAt = Instant.now();
    }

    public void complete() {
        status = Status.COMPLETED;
        completedAt = Instant.now();
    }

    public void reject(String reason) {
        status = Status.REJECTED;
        failureReason = reason;
        completedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public long getAmountCents() { return amountCents; }
    public Status getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
