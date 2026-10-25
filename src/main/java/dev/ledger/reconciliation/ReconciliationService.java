package dev.ledger.reconciliation;

import dev.ledger.common.ApiException;
import dev.ledger.common.CorrelationIds;
import dev.ledger.event.EventEnvelope;
import dev.ledger.event.IdempotentEventProcessor;
import dev.ledger.event.OutboxService;
import dev.ledger.payment.Payment;
import dev.ledger.payment.PaymentLedgerPostingRepository;
import dev.ledger.payment.PaymentRepository;
import dev.ledger.reconciliation.ReconciliationDtos.ExceptionResponse;
import dev.ledger.reconciliation.ReconciliationDtos.RunResponse;
import dev.ledger.settlement.SettlementRecord;
import dev.ledger.settlement.SettlementRecordRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ReconciliationService {

    public static final String MISMATCH = "reconciliation.mismatch.v1";

    private final PaymentRepository payments;
    private final PaymentLedgerPostingRepository postings;
    private final SettlementRecordRepository settlements;
    private final ReconciliationExceptionRepository exceptions;
    private final ReconciliationActionRepository actions;
    private final OutboxService outbox;
    private final IdempotentEventProcessor idempotency;
    private final MeterRegistry meters;

    public ReconciliationService(PaymentRepository payments, PaymentLedgerPostingRepository postings,
                                 SettlementRecordRepository settlements,
                                 ReconciliationExceptionRepository exceptions,
                                 ReconciliationActionRepository actions, OutboxService outbox,
                                 IdempotentEventProcessor idempotency, MeterRegistry meters) {
        this.payments = payments;
        this.postings = postings;
        this.settlements = settlements;
        this.exceptions = exceptions;
        this.actions = actions;
        this.outbox = outbox;
        this.idempotency = idempotency;
        this.meters = meters;
        meters.gauge("paymentops.reconciliation.open", exceptions,
                repository -> repository.countByStatus(ReconciliationException.Status.OPEN)
                        + repository.countByStatus(ReconciliationException.Status.ACKNOWLEDGED));
    }

    @Transactional
    public void processSettled(EventEnvelope event) {
        idempotency.process("reconciliation-events", event,
                () -> reconcilePayment(UUID.fromString(event.aggregateId())));
    }

    @Transactional
    public RunResponse reconcileAll() {
        int checked = 0;
        for (Payment payment : payments.findAll()) {
            if (payment.getStatus() == Payment.Status.CAPTURED
                    || payment.getStatus() == Payment.Status.SETTLED
                    || payment.getStatus() == Payment.Status.FAILED) {
                reconcilePayment(payment.getId());
                checked++;
            }
        }
        reconcileExternalOnly();
        long open = exceptions.countByStatus(ReconciliationException.Status.OPEN)
                + exceptions.countByStatus(ReconciliationException.Status.ACKNOWLEDGED);
        return new RunResponse(checked, Math.toIntExact(open));
    }

    @Transactional
    public void reconcilePayment(UUID paymentId) {
        Payment payment = payments.findById(paymentId).orElseThrow();
        List<SettlementRecord> all = settlements.findByPaymentIdOrderByReceivedAt(paymentId);
        List<SettlementRecord> settled = all.stream()
                .filter(r -> r.getStatus() == SettlementRecord.Status.SETTLED).toList();
        Set<ReconciliationException.Classification> active =
                EnumSet.noneOf(ReconciliationException.Classification.class);

        if (settled.isEmpty() && postings.countByPaymentId(paymentId) > 0) {
            active.add(ReconciliationException.Classification.MISSING_SETTLEMENT);
            mismatch(payment, ReconciliationException.Classification.MISSING_SETTLEMENT,
                    payment.getAmountCents(), null, "No settled external record exists");
        }
        if (settled.size() > 1) {
            active.add(ReconciliationException.Classification.DUPLICATE_SETTLEMENT);
            mismatch(payment, ReconciliationException.Classification.DUPLICATE_SETTLEMENT,
                    payment.getAmountCents(), settled.stream().mapToLong(SettlementRecord::getAmountCents).sum(),
                    "External references=" + settled.stream().map(SettlementRecord::getExternalReference).toList());
        }
        SettlementRecord amountMismatch = settled.stream()
                .filter(record -> record.getAmountCents() != payment.getAmountCents()).findFirst().orElse(null);
        if (amountMismatch != null) {
            active.add(ReconciliationException.Classification.AMOUNT_MISMATCH);
            mismatch(payment, ReconciliationException.Classification.AMOUNT_MISMATCH,
                    payment.getAmountCents(), amountMismatch.getAmountCents(),
                    "Settlement record=" + amountMismatch.getId());
        }
        boolean wasDelayed = all.stream().anyMatch(r -> r.getStatus() == SettlementRecord.Status.DELAYED);
        if (wasDelayed && !settled.isEmpty()) {
            active.add(ReconciliationException.Classification.LATE_SETTLEMENT);
            ReconciliationException late = mismatch(payment,
                    ReconciliationException.Classification.LATE_SETTLEMENT,
                    payment.getAmountCents(), settled.getFirst().getAmountCents(),
                    "Delayed record later received as settled");
            if (late.getStatus() != ReconciliationException.Status.RESOLVED) {
                resolve(late, "system", "Late settlement arrived and reconciled");
            }
        }

        for (ReconciliationException existing : exceptions.findByPaymentId(paymentId)) {
            if (!active.contains(existing.getClassification())
                    && existing.getStatus() != ReconciliationException.Status.RESOLVED) {
                resolve(existing, "system", "Underlying records now reconcile");
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ExceptionResponse> list(ReconciliationException.Status status) {
        List<ReconciliationException> result = status == null
                ? exceptions.findAll()
                : exceptions.findByStatusOrderByFirstDetectedAt(status);
        return result.stream().map(ExceptionResponse::from).toList();
    }

    @Transactional
    public ExceptionResponse acknowledge(UUID exceptionId, String actor, String notes) {
        ReconciliationException exception = get(exceptionId);
        exception.acknowledge();
        actions.save(new ReconciliationAction(exceptionId, ReconciliationAction.Action.ACKNOWLEDGED,
                actor, notes));
        return ExceptionResponse.from(exception);
    }

    @Transactional
    public ExceptionResponse resolve(UUID exceptionId, String actor, String notes) {
        ReconciliationException exception = get(exceptionId);
        resolve(exception, actor, notes);
        return ExceptionResponse.from(exception);
    }

    private ReconciliationException mismatch(Payment payment,
                                             ReconciliationException.Classification classification,
                                             Long expected, Long actual, String evidence) {
        ReconciliationException existing = exceptions.findByPaymentIdAndClassification(
                payment.getId(), classification).orElse(null);
        boolean publish = existing == null || existing.getStatus() == ReconciliationException.Status.RESOLVED;
        ReconciliationException exception;
        if (existing == null) {
            exception = exceptions.save(new ReconciliationException(
                    payment.getId(), classification, expected, actual, evidence));
            actions.save(new ReconciliationAction(exception.getId(), ReconciliationAction.Action.DETECTED,
                    "reconciliation-service", evidence));
        } else {
            boolean reopening = existing.getStatus() == ReconciliationException.Status.RESOLVED;
            existing.detected(expected, actual, evidence);
            exception = existing;
            if (reopening) {
                actions.save(new ReconciliationAction(exception.getId(), ReconciliationAction.Action.REOPENED,
                        "reconciliation-service", evidence));
            }
        }
        if (publish) {
            outbox.enqueue(MISMATCH, payment.getId(), payment.getCorrelationId(), null,
                    "reconciliation-service", Map.of("exceptionId", exception.getId(),
                            "paymentId", payment.getId(), "classification", classification));
            meters.counter("paymentops.reconciliation.mismatches",
                    "classification", classification.name()).increment();
        }
        return exception;
    }

    private void reconcileExternalOnly() {
        for (SettlementRecord record : settlements.findAll()) {
            if (record.getPaymentId() == null) {
                String evidence = "Settlement record=" + record.getId();
                if (exceptions.existsByClassificationAndEvidence(
                        ReconciliationException.Classification.EXTERNAL_ONLY, evidence)) {
                    continue;
                }
                ReconciliationException exception = exceptions.save(new ReconciliationException(
                        null, ReconciliationException.Classification.EXTERNAL_ONLY,
                        null, record.getAmountCents(), evidence));
                actions.save(new ReconciliationAction(exception.getId(), ReconciliationAction.Action.DETECTED,
                        "reconciliation-service", "No internal payment matches the external settlement"));
                outbox.enqueue(MISMATCH, record.getId(), CorrelationIds.current(), null,
                        "reconciliation-service", Map.of("exceptionId", exception.getId(),
                                "classification", "EXTERNAL_ONLY"));
            }
        }
    }

    private ReconciliationException get(UUID id) {
        return exceptions.findById(id).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "RECONCILIATION_EXCEPTION_NOT_FOUND", "Exception not found"));
    }

    private void resolve(ReconciliationException exception, String actor, String notes) {
        exception.resolve();
        actions.save(new ReconciliationAction(exception.getId(), ReconciliationAction.Action.RESOLVED,
                actor, notes));
    }
}
