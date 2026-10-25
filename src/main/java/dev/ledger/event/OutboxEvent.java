package dev.ledger.event;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    public enum Status {PENDING, PUBLISHED, DEAD_LETTER}

    @Id
    private UUID eventId;
    private String eventType;
    private String aggregateId;
    private UUID correlationId;
    private UUID causationId;
    private int schemaVersion;
    private String producer;
    private String payload;
    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;
    private int attempts;
    private Instant nextAttemptAt = Instant.now();
    private Instant occurredAt = Instant.now();
    private Instant publishedAt;
    private String lastError;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID eventId, String eventType, String aggregateId, UUID correlationId,
                       UUID causationId, String producer, String payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.correlationId = correlationId;
        this.causationId = causationId;
        this.schemaVersion = 1;
        this.producer = producer;
        this.payload = payload;
    }

    public void published() {
        status = Status.PUBLISHED;
        publishedAt = Instant.now();
        lastError = null;
    }

    public void failed(Throwable failure, int maxAttempts) {
        attempts++;
        lastError = abbreviate(failure.getMessage());
        if (attempts >= maxAttempts) {
            status = Status.DEAD_LETTER;
        } else {
            long delaySeconds = Math.min(300, 1L << Math.min(attempts, 8));
            nextAttemptAt = Instant.now().plus(delaySeconds, ChronoUnit.SECONDS);
        }
    }

    public void replay() {
        status = Status.PENDING;
        attempts = 0;
        nextAttemptAt = Instant.now();
        lastError = null;
        publishedAt = null;
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "Unknown event processing failure";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getAggregateId() { return aggregateId; }
    public UUID getCorrelationId() { return correlationId; }
    public UUID getCausationId() { return causationId; }
    public int getSchemaVersion() { return schemaVersion; }
    public String getProducer() { return producer; }
    public String getPayload() { return payload; }
    public Status getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getLastError() { return lastError; }
}
