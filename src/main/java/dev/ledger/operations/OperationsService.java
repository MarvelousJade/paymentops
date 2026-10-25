package dev.ledger.operations;

import dev.ledger.common.ApiException;
import dev.ledger.event.OutboxEvent;
import dev.ledger.event.OutboxEventRepository;
import dev.ledger.notification.WebhookDelivery;
import dev.ledger.notification.WebhookDeliveryRepository;
import dev.ledger.payment.Payment;
import dev.ledger.payment.PaymentDtos.PaymentResponse;
import dev.ledger.payment.PaymentDtos.TimelineResponse;
import dev.ledger.payment.PaymentEventRepository;
import dev.ledger.payment.PaymentRepository;
import dev.ledger.reconciliation.ReconciliationException;
import dev.ledger.reconciliation.ReconciliationExceptionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OperationsService {

    private final PaymentRepository payments;
    private final PaymentEventRepository timeline;
    private final OutboxEventRepository outbox;
    private final ReconciliationExceptionRepository exceptions;
    private final WebhookDeliveryRepository deliveries;

    public OperationsService(PaymentRepository payments, PaymentEventRepository timeline,
                             OutboxEventRepository outbox,
                             ReconciliationExceptionRepository exceptions,
                             WebhookDeliveryRepository deliveries) {
        this.payments = payments;
        this.timeline = timeline;
        this.outbox = outbox;
        this.exceptions = exceptions;
        this.deliveries = deliveries;
    }

    @Transactional(readOnly = true)
    public List<PaymentSummary> search(UUID correlationId) {
        return payments.findByCorrelationIdOrderByCreatedAt(correlationId).stream()
                .map(payment -> new PaymentSummary(PaymentResponse.from(payment),
                        timeline.findByPaymentIdOrderByOccurredAtAsc(payment.getId()).stream()
                                .map(TimelineResponse::from).toList())).toList();
    }

    @Transactional
    public void replay(UUID eventId) {
        OutboxEvent event = outbox.findByEventIdAndStatus(eventId, OutboxEvent.Status.DEAD_LETTER)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "DEAD_LETTER_EVENT_NOT_FOUND", "Dead-letter event not found"));
        event.replay();
    }

    @Transactional(readOnly = true)
    public OperationalHealth health() {
        return new OperationalHealth(
                outbox.countByStatus(OutboxEvent.Status.PENDING),
                outbox.countByStatus(OutboxEvent.Status.DEAD_LETTER),
                exceptions.countByStatus(ReconciliationException.Status.OPEN)
                        + exceptions.countByStatus(ReconciliationException.Status.ACKNOWLEDGED),
                deliveries.findAll().stream()
                        .filter(d -> d.getStatus() == WebhookDelivery.Status.DEAD_LETTER).count());
    }

    public record PaymentSummary(PaymentResponse payment, List<TimelineResponse> timeline) {
    }

    public record OperationalHealth(long pendingOutboxEvents, long deadLetterEvents,
                                    long openReconciliationExceptions, long deadLetterWebhooks) {
    }
}
