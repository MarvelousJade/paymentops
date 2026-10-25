package dev.ledger.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledger.event.EventEnvelope;
import dev.ledger.event.IdempotentEventProcessor;
import dev.ledger.event.OutboxService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class NotificationService {

    public static final String DEAD_LETTER = "notification.dead-letter.v1";

    private final WebhookDeliveryRepository deliveries;
    private final IdempotentEventProcessor idempotency;
    private final OutboxService outbox;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;
    private final String endpoint;
    private final String signingSecret;
    private final int maxAttempts;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public NotificationService(WebhookDeliveryRepository deliveries,
                               IdempotentEventProcessor idempotency, OutboxService outbox,
                               ObjectMapper mapper, MeterRegistry meters,
                               @Value("${paymentops.webhook.endpoint:simulated://merchant}") String endpoint,
                               @Value("${paymentops.webhook.signing-secret:local-development-only}") String signingSecret,
                               @Value("${paymentops.webhook.max-attempts:3}") int maxAttempts) {
        this.deliveries = deliveries;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.mapper = mapper;
        this.meters = meters;
        this.endpoint = endpoint;
        this.signingSecret = signingSecret;
        this.maxAttempts = maxAttempts;
    }

    @Transactional
    public void queue(EventEnvelope event) {
        idempotency.process("notification-service", event, () -> {
            try {
                String payload = mapper.writeValueAsString(event);
                deliveries.save(new WebhookDelivery(UUID.fromString(event.aggregateId()), event.eventId(),
                        endpoint, payload, sign(payload)));
                meters.counter("paymentops.webhooks.queued").increment();
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Webhook payload cannot be serialized", e);
            }
        });
    }

    @Scheduled(fixedDelayString = "${paymentops.webhook.poll-delay-ms:1000}",
            initialDelayString = "${paymentops.webhook.initial-delay-ms:0}")
    @Transactional
    public void deliverDue() {
        List<WebhookDelivery> due = deliveries.findByStatusInAndNextAttemptAtBeforeOrderByCreatedAt(
                Set.of(WebhookDelivery.Status.PENDING, WebhookDelivery.Status.RETRYING),
                Instant.now(), PageRequest.of(0, 25));
        for (WebhookDelivery delivery : due) {
            attempt(delivery);
        }
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponse> history(UUID paymentId) {
        return deliveries.findByPaymentIdOrderByCreatedAt(paymentId).stream()
                .map(DeliveryResponse::from).toList();
    }

    private void attempt(WebhookDelivery delivery) {
        try {
            if (delivery.getEndpoint().startsWith("simulated://")) {
                delivery.delivered();
            } else {
                HttpRequest request = HttpRequest.newBuilder(URI.create(delivery.getEndpoint()))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .header("X-PaymentOps-Signature", "sha256=" + delivery.getSignature())
                        .POST(HttpRequest.BodyPublishers.ofString(delivery.getPayload()))
                        .build();
                HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    delivery.delivered();
                } else {
                    fail(delivery, "HTTP " + response.statusCode());
                }
            }
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            fail(delivery, failure.getClass().getSimpleName());
        }
        meters.counter("paymentops.webhooks.delivery", "status", delivery.getStatus().name()).increment();
    }

    private void fail(WebhookDelivery delivery, String error) {
        if (delivery.failed(error, maxAttempts)) {
            outbox.enqueue(DEAD_LETTER, delivery.getId(), UUID.randomUUID(),
                    delivery.getSourceEventId(), "notification-service",
                    new DeadLetterPayload(delivery.getId(), delivery.getPaymentId(), error));
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign webhook", e);
        }
    }

    private record DeadLetterPayload(UUID deliveryId, UUID paymentId, String reason) {
    }

    public record DeliveryResponse(UUID id, UUID paymentId, UUID sourceEventId, String endpoint,
                                   WebhookDelivery.Status status, int attempts, String lastError,
                                   Instant createdAt, Instant updatedAt) {
        static DeliveryResponse from(WebhookDelivery delivery) {
            return new DeliveryResponse(delivery.getId(), delivery.getPaymentId(),
                    delivery.getSourceEventId(), delivery.getEndpoint(), delivery.getStatus(),
                    delivery.getAttempts(), delivery.getLastError(), delivery.getCreatedAt(),
                    delivery.getUpdatedAt());
        }
    }
}
