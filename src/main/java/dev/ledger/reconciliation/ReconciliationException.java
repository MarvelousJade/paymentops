package dev.ledger.reconciliation;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_exceptions")
public class ReconciliationException {

    public enum Classification {
        MISSING_SETTLEMENT, EXTERNAL_ONLY, AMOUNT_MISMATCH, DUPLICATE_SETTLEMENT, LATE_SETTLEMENT
    }

    public enum Status {OPEN, ACKNOWLEDGED, RESOLVED}

    @Id
    private UUID id;
    private UUID paymentId;
    @Enumerated(EnumType.STRING)
    private Classification classification;
    @Enumerated(EnumType.STRING)
    private Status status;
    private Long expectedAmount;
    private Long actualAmount;
    private String evidence;
    private Instant firstDetectedAt;
    private Instant lastDetectedAt;
    private Instant resolvedAt;

    protected ReconciliationException() {
    }

    public ReconciliationException(UUID paymentId, Classification classification,
                                   Long expectedAmount, Long actualAmount, String evidence) {
        this.id = UUID.randomUUID();
        this.paymentId = paymentId;
        this.classification = classification;
        this.status = Status.OPEN;
        this.expectedAmount = expectedAmount;
        this.actualAmount = actualAmount;
        this.evidence = evidence;
        this.firstDetectedAt = Instant.now();
        this.lastDetectedAt = firstDetectedAt;
    }

    public void detected(Long expected, Long actual, String latestEvidence) {
        expectedAmount = expected;
        actualAmount = actual;
        evidence = latestEvidence;
        lastDetectedAt = Instant.now();
        if (status == Status.RESOLVED) {
            status = Status.OPEN;
            resolvedAt = null;
        }
    }

    public void acknowledge() {
        if (status == Status.OPEN) {
            status = Status.ACKNOWLEDGED;
        }
    }

    public void resolve() {
        status = Status.RESOLVED;
        resolvedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public Classification getClassification() { return classification; }
    public Status getStatus() { return status; }
    public Long getExpectedAmount() { return expectedAmount; }
    public Long getActualAmount() { return actualAmount; }
    public String getEvidence() { return evidence; }
    public Instant getFirstDetectedAt() { return firstDetectedAt; }
    public Instant getLastDetectedAt() { return lastDetectedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}
