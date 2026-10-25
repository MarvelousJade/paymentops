package dev.ledger.risk;

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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class RiskRulesService {

    public static final String RISK_DECIDED = "payment.risk-decided.v1";
    public static final String APPROVED = "payment.approved.v1";
    public static final String REJECTED = "payment.rejected.v1";

    private final PaymentRepository payments;
    private final RiskDecisionRepository decisions;
    private final PaymentService paymentService;
    private final OutboxService outbox;
    private final IdempotentEventProcessor idempotency;
    private final MeterRegistry meters;
    private final long perTransactionLimitCents;
    private final long dailyLimitCents;
    private final long velocityReviewCount;

    public RiskRulesService(PaymentRepository payments, RiskDecisionRepository decisions,
                            PaymentService paymentService, OutboxService outbox,
                            IdempotentEventProcessor idempotency, MeterRegistry meters,
                            @Value("${paymentops.risk.per-transaction-limit-cents:1000000}") long perTransactionLimitCents,
                            @Value("${paymentops.risk.daily-limit-cents:2500000}") long dailyLimitCents,
                            @Value("${paymentops.risk.velocity-review-count:10}") long velocityReviewCount) {
        this.payments = payments;
        this.decisions = decisions;
        this.paymentService = paymentService;
        this.outbox = outbox;
        this.idempotency = idempotency;
        this.meters = meters;
        this.perTransactionLimitCents = perTransactionLimitCents;
        this.dailyLimitCents = dailyLimitCents;
        this.velocityReviewCount = velocityReviewCount;
    }

    @Transactional
    public void process(EventEnvelope event) {
        idempotency.process("risk-rules", event, () -> decide(event));
    }

    private void decide(EventEnvelope event) {
        UUID paymentId = UUID.fromString(event.aggregateId());
        Payment payment = payments.findWithLockById(paymentId).orElseThrow();
        if (payment.getStatus() != Payment.Status.REQUESTED) {
            return;
        }

        Decision result = evaluate(payment);
        decisions.save(new RiskDecision(paymentId, event.eventId(), result.outcome(),
                result.ruleCode(), result.explanation()));

        Payment.Status next = switch (result.outcome()) {
            case APPROVE -> Payment.Status.AUTHORIZED;
            case REVIEW -> Payment.Status.REVIEW_REQUIRED;
            case REJECT -> Payment.Status.REJECTED;
        };
        Payment.Status old = payment.transition(Payment.Status.REQUESTED, next);
        String resultEvent = result.outcome() == RiskDecision.Outcome.APPROVE ? APPROVED
                : result.outcome() == RiskDecision.Outcome.REJECT ? REJECTED : RISK_DECIDED;
        Map<String, Object> details = Map.of("outcome", result.outcome(), "ruleCode", result.ruleCode(),
                "explanation", result.explanation());
        UUID resultEventId = outbox.enqueue(resultEvent, paymentId, payment.getCorrelationId(),
                event.eventId(), "risk-rules", new PaymentReference(paymentId));
        paymentService.append(payment, resultEventId, resultEvent, old, next, "risk-rules", details);

        // A neutral decision event is also useful to analytics consumers.
        if (!RISK_DECIDED.equals(resultEvent)) {
            outbox.enqueue(RISK_DECIDED, paymentId, payment.getCorrelationId(), event.eventId(),
                    "risk-rules", details);
        }
        meters.counter("paymentops.risk.decisions", "outcome", result.outcome().name(),
                "rule", result.ruleCode()).increment();
    }

    Decision evaluate(Payment payment) {
        if (payment.getAmountCents() > perTransactionLimitCents) {
            return new Decision(RiskDecision.Outcome.REJECT, "PER_TRANSACTION_LIMIT",
                    "Payment exceeds the configured per-transaction limit");
        }
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        if (payments.sumActiveAmountSince(payment.getMerchantId(), startOfDay,
                Set.of(Payment.Status.REJECTED, Payment.Status.CANCELLED, Payment.Status.FAILED))
                > dailyLimitCents) {
            return new Decision(RiskDecision.Outcome.REJECT, "DAILY_LIMIT",
                    "Merchant daily payment total exceeds the configured limit");
        }
        Instant velocityWindow = Instant.now().minus(1, ChronoUnit.MINUTES);
        if (payments.countByMerchantIdAndCreatedAtAfter(payment.getMerchantId(), velocityWindow)
                >= velocityReviewCount) {
            return new Decision(RiskDecision.Outcome.REVIEW, "VELOCITY_REVIEW",
                    "Payment velocity requires operator review");
        }
        return new Decision(RiskDecision.Outcome.APPROVE, "ALL_RULES_PASSED",
                "Payment passed deterministic risk rules");
    }

    record Decision(RiskDecision.Outcome outcome, String ruleCode, String explanation) {
    }
}
