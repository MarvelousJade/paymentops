package dev.ledger.notification;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "webhook_deliveries")
public class WebhookDelivery {

    public enum Status {PENDING, DELIVERED, RETRYING, DEAD_LETTER}

    @Id
    private UUID id;
    private UUID paymentId;
    private UUID sourceEventId;
    private String endpoint;
    private String payload;
    private String signature;
    @Enumerated(EnumType.STRING)
    private Status status;
    private int attempts;
    private Instant nextAttemptAt;
    private String lastError;
    private Instant createdAt;
    private Instant updatedAt;

    protected WebhookDelivery() {
    }

    public WebhookDelivery(UUID paymentId, UUID sourceEventId, String endpoint,
                           String payload, String signature) {
        this.id = UUID.randomUUID();
        this.paymentId = paymentId;
        this.sourceEventId = sourceEventId;
        this.endpoint = endpoint;
        this.payload = payload;
        this.signature = signature;
        this.status = Status.PENDING;
        this.nextAttemptAt = Instant.now();
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void delivered() {
        attempts++;
        status = Status.DELIVERED;
        lastError = null;
        updatedAt = Instant.now();
    }

    public boolean failed(String error, int maxAttempts) {
        attempts++;
        lastError = error == null ? "Webhook delivery failed" : error.substring(0, Math.min(error.length(), 500));
        updatedAt = Instant.now();
        if (attempts >= maxAttempts) {
            status = Status.DEAD_LETTER;
            return true;
        }
        status = Status.RETRYING;
        nextAttemptAt = Instant.now().plus(Math.min(300, 1L << attempts), ChronoUnit.SECONDS);
        return false;
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getSourceEventId() { return sourceEventId; }
    public String getEndpoint() { return endpoint; }
    public String getPayload() { return payload; }
    public String getSignature() { return signature; }
    public Status getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
