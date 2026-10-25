package dev.ledger.settlement;

import dev.ledger.event.EventEnvelope;
import dev.ledger.event.IdempotentEventProcessor;
import dev.ledger.event.OutboxService;
import dev.ledger.payment.Payment;
import dev.ledger.payment.PaymentDtos.PaymentReference;
import dev.ledger.payment.PaymentRepository;
import dev.ledger.payment.PaymentService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class SettlementSimulatorService {

    public enum Behavior {SETTLE, REJECT, DELAY, DUPLICATE}

    public static final String SETTLED = "payment.settled.v1";
    public static final String SETTLEMENT_FAILED = "payment.settlement-failed.v1";
    public static final String SETTLEMENT_DELAYED = "payment.settlement-delayed.v1";

    private final SettlementRecordRepository records;
    private final PaymentRepository payments;
    private final PaymentService paymentService;
    private final OutboxService outbox;
    private final IdempotentEventProcessor idempotency;
    private final MeterRegistry meters;
    private final Behavior behavior;

    public SettlementSimulatorService(SettlementRecordRepository records, PaymentRepository payments,
                                      PaymentService paymentService, OutboxService outbox,
                                      IdempotentEventProcessor idempotency, MeterRegistry meters,
                                      @Value("${paymentops.settlement.behavior:SETTLE}") Behavior behavior) {
        this.records = records;
        this.payments = payments;
        this.paymentService = paymentService;
        this.outbox = outbox;
        this.idempotency = idempotency;
        this.meters = meters;
        this.behavior = behavior;
    }

    @Transactional
    public void process(EventEnvelope event) {
        idempotency.process("settlement-simulator", event, () -> settle(event, behavior));
    }

    @Transactional
    public SettlementRecord importRecord(UUID paymentId, String externalReference, long amountCents,
                                         String currency, SettlementRecord.Status status) {
        String raw = raw(paymentId, externalReference, amountCents, currency, status);
        return records.save(new SettlementRecord(paymentId, externalReference, amountCents, currency,
                status, null, status == SettlementRecord.Status.SETTLED ? Instant.now() : null, raw));
    }

    @Transactional
    public void completeDelayed(UUID paymentId) {
        Payment payment = payments.findWithLockById(paymentId).orElseThrow();
        if (payment.getStatus() != Payment.Status.CAPTURED) {
            return;
        }
        String reference = "rail-" + paymentId;
        SettlementRecord record = records.save(new SettlementRecord(paymentId, reference,
                payment.getAmountCents(), payment.getCurrency(), SettlementRecord.Status.SETTLED,
                UUID.randomUUID(), Instant.now(),
                raw(paymentId, reference, payment.getAmountCents(), payment.getCurrency(),
                        SettlementRecord.Status.SETTLED)));
        finishSettlement(payment, record.getSourceEventId(), record);
    }

    private void settle(EventEnvelope event, Behavior selected) {
        UUID paymentId = UUID.fromString(event.aggregateId());
        Payment payment = payments.findWithLockById(paymentId).orElseThrow();
        if (payment.getStatus() != Payment.Status.CAPTURED) {
            return;
        }
        String reference = "rail-" + paymentId;
        SettlementRecord.Status status = switch (selected) {
            case SETTLE, DUPLICATE -> SettlementRecord.Status.SETTLED;
            case REJECT -> SettlementRecord.Status.REJECTED;
            case DELAY -> SettlementRecord.Status.DELAYED;
        };
        SettlementRecord record = records.save(new SettlementRecord(paymentId, reference,
                payment.getAmountCents(), payment.getCurrency(), status, event.eventId(),
                status == SettlementRecord.Status.SETTLED ? Instant.now() : null,
                raw(paymentId, reference, payment.getAmountCents(), payment.getCurrency(), status)));

        if (selected == Behavior.DUPLICATE) {
            records.save(new SettlementRecord(paymentId, reference, payment.getAmountCents(),
                    payment.getCurrency(), SettlementRecord.Status.SETTLED, UUID.randomUUID(),
                    Instant.now(), raw(paymentId, reference, payment.getAmountCents(),
                    payment.getCurrency(), SettlementRecord.Status.SETTLED)));
        }

        if (status == SettlementRecord.Status.SETTLED) {
            finishSettlement(payment, event.eventId(), record);
        } else if (status == SettlementRecord.Status.REJECTED) {
            Payment.Status old = payment.transition(Payment.Status.CAPTURED, Payment.Status.FAILED);
            UUID failedEvent = outbox.enqueue(SETTLEMENT_FAILED, paymentId, payment.getCorrelationId(),
                    event.eventId(), "settlement-simulator", new PaymentReference(paymentId));
            paymentService.append(payment, failedEvent, SETTLEMENT_FAILED, old, payment.getStatus(),
                    "settlement-simulator", Map.of("externalReference", reference));
        } else {
            UUID delayedEvent = outbox.enqueue(SETTLEMENT_DELAYED, paymentId, payment.getCorrelationId(),
                    event.eventId(), "settlement-simulator", new PaymentReference(paymentId));
            paymentService.append(payment, delayedEvent, SETTLEMENT_DELAYED, payment.getStatus(),
                    payment.getStatus(), "settlement-simulator", Map.of("externalReference", reference));
        }
        meters.counter("paymentops.settlement.records", "status", status.name()).increment();
    }

    private void finishSettlement(Payment payment, UUID causationId, SettlementRecord record) {
        Payment.Status old = payment.transition(Payment.Status.CAPTURED, Payment.Status.SETTLED);
        UUID settledEvent = outbox.enqueue(SETTLED, payment.getId(), payment.getCorrelationId(),
                causationId, "settlement-simulator", new PaymentReference(payment.getId()));
        paymentService.append(payment, settledEvent, SETTLED, old, payment.getStatus(),
                "settlement-simulator", Map.of("settlementRecordId", record.getId(),
                        "externalReference", record.getExternalReference()));
    }

    private static String raw(UUID paymentId, String reference, long amount, String currency,
                              SettlementRecord.Status status) {
        return "paymentId=%s|reference=%s|amountCents=%d|currency=%s|status=%s"
                .formatted(paymentId, reference, amount, currency, status);
    }
}
