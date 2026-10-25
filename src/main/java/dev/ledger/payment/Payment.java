package dev.ledger.payment;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    public enum Status {
        REQUESTED, AUTHORIZED, REVIEW_REQUIRED, REJECTED,
        CAPTURE_PENDING, CAPTURED, SETTLED, CANCELLED,
        PARTIALLY_REFUNDED, REFUNDED, FAILED
    }

    private static final Set<Status> CANCELLABLE =
            EnumSet.of(Status.REQUESTED, Status.AUTHORIZED, Status.REVIEW_REQUIRED);
    private static final Set<Status> REFUNDABLE =
            EnumSet.of(Status.CAPTURED, Status.SETTLED, Status.PARTIALLY_REFUNDED);

    @Id
    private UUID id;
    private Long merchantId;
    private String idempotencyKey;
    private Long sourceAccountId;
    private Long destinationAccountId;
    private long amountCents;
    private long refundedAmountCents;
    private String currency;
    private String paymentToken;
    @Enumerated(EnumType.STRING)
    private Status status;
    private UUID correlationId;
    @Version
    private long version;
    private Instant createdAt;
    private Instant updatedAt;

    protected Payment() {
    }

    public Payment(Long merchantId, String idempotencyKey, Long sourceAccountId, Long destinationAccountId,
                   long amountCents, String currency, String paymentToken, UUID correlationId) {
        this.id = UUID.randomUUID();
        this.merchantId = merchantId;
        this.idempotencyKey = idempotencyKey;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amountCents = amountCents;
        this.currency = currency;
        this.paymentToken = paymentToken;
        this.correlationId = correlationId;
        this.status = Status.REQUESTED;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public Status transition(Status expected, Status next) {
        if (status != expected) {
            throw new IllegalStateException("Expected payment status " + expected + " but was " + status);
        }
        Status old = status;
        status = next;
        updatedAt = Instant.now();
        return old;
    }

    public Status cancel() {
        if (!CANCELLABLE.contains(status)) {
            throw new IllegalStateException("Payment in " + status + " cannot be cancelled");
        }
        Status old = status;
        status = Status.CANCELLED;
        updatedAt = Instant.now();
        return old;
    }

    public void completeRefund(long cents) {
        if (!REFUNDABLE.contains(status)) {
            throw new IllegalStateException("Payment in " + status + " cannot be refunded");
        }
        if (cents <= 0 || refundedAmountCents + cents > amountCents) {
            throw new IllegalArgumentException("Refund exceeds remaining payment amount");
        }
        refundedAmountCents += cents;
        status = refundedAmountCents == amountCents ? Status.REFUNDED : Status.PARTIALLY_REFUNDED;
        updatedAt = Instant.now();
    }

    public boolean canRefund(long cents) {
        return REFUNDABLE.contains(status) && cents > 0 && refundedAmountCents + cents <= amountCents;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Long getMerchantId() { return merchantId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Long getSourceAccountId() { return sourceAccountId; }
    public Long getDestinationAccountId() { return destinationAccountId; }
    public long getAmountCents() { return amountCents; }
    public long getRefundedAmountCents() { return refundedAmountCents; }
    public String getCurrency() { return currency; }
    public String getPaymentToken() { return paymentToken; }
    public Status getStatus() { return status; }
    public UUID getCorrelationId() { return correlationId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
