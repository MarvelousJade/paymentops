package dev.ledger.settlement;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "settlement_records")
public class SettlementRecord {

    public enum Status {SETTLED, REJECTED, DELAYED}

    @Id
    private UUID id;
    private UUID paymentId;
    private String externalReference;
    private long amountCents;
    private String currency;
    @Enumerated(EnumType.STRING)
    private Status status;
    private UUID sourceEventId;
    private Instant settledAt;
    private Instant receivedAt;
    private String rawRecord;

    protected SettlementRecord() {
    }

    public SettlementRecord(UUID paymentId, String externalReference, long amountCents, String currency,
                            Status status, UUID sourceEventId, Instant settledAt, String rawRecord) {
        this.id = UUID.randomUUID();
        this.paymentId = paymentId;
        this.externalReference = externalReference;
        this.amountCents = amountCents;
        this.currency = currency;
        this.status = status;
        this.sourceEventId = sourceEventId;
        this.settledAt = settledAt;
        this.receivedAt = Instant.now();
        this.rawRecord = rawRecord;
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public String getExternalReference() { return externalReference; }
    public long getAmountCents() { return amountCents; }
    public String getCurrency() { return currency; }
    public Status getStatus() { return status; }
    public UUID getSourceEventId() { return sourceEventId; }
    public Instant getSettledAt() { return settledAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public String getRawRecord() { return rawRecord; }
}
