package dev.ledger.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_events")
public class PaymentEvent {

    @Id
    private UUID id;
    private UUID paymentId;
    private UUID eventId;
    private String eventType;
    private String fromStatus;
    private String toStatus;
    private String actor;
    private String details;
    @Column(insertable = false, updatable = false)
    private Instant occurredAt;

    protected PaymentEvent() {
    }

    public PaymentEvent(UUID paymentId, UUID eventId, String eventType, Payment.Status fromStatus,
                        Payment.Status toStatus, String actor, String details) {
        this.id = UUID.randomUUID();
        this.paymentId = paymentId;
        this.eventId = eventId;
        this.eventType = eventType;
        this.fromStatus = fromStatus == null ? null : fromStatus.name();
        this.toStatus = toStatus == null ? null : toStatus.name();
        this.actor = actor;
        this.details = details;
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getActor() { return actor; }
    public String getDetails() { return details; }
    public Instant getOccurredAt() { return occurredAt; }
}
