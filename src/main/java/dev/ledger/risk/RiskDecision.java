package dev.ledger.risk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "risk_decisions")
public class RiskDecision {

    public enum Outcome {APPROVE, REVIEW, REJECT}

    @Id
    private UUID id;
    private UUID paymentId;
    private UUID sourceEventId;
    @Enumerated(EnumType.STRING)
    private Outcome outcome;
    private String ruleCode;
    private String explanation;
    @Column(insertable = false, updatable = false)
    private Instant decidedAt;

    protected RiskDecision() {
    }

    public RiskDecision(UUID paymentId, UUID sourceEventId, Outcome outcome,
                        String ruleCode, String explanation) {
        this.id = UUID.randomUUID();
        this.paymentId = paymentId;
        this.sourceEventId = sourceEventId;
        this.outcome = outcome;
        this.ruleCode = ruleCode;
        this.explanation = explanation;
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getSourceEventId() { return sourceEventId; }
    public Outcome getOutcome() { return outcome; }
    public String getRuleCode() { return ruleCode; }
    public String getExplanation() { return explanation; }
    public Instant getDecidedAt() { return decidedAt; }
}
