package dev.ledger.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledger.account.AccountRepository;
import dev.ledger.audit.AuditService;
import dev.ledger.common.ApiException;
import dev.ledger.event.OutboxService;
import dev.ledger.payment.PaymentDtos.PaymentReference;
import dev.ledger.payment.PaymentDtos.PaymentRequested;
import dev.ledger.payment.PaymentDtos.PaymentResponse;
import dev.ledger.payment.PaymentDtos.RefundRequested;
import dev.ledger.payment.PaymentDtos.RefundResponse;
import dev.ledger.payment.PaymentDtos.TimelineResponse;
import dev.ledger.transfer.RateLimiter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    public static final String REQUESTED = "payment.requested.v1";
    public static final String CAPTURE_REQUESTED = "payment.capture-requested.v1";
    public static final String CANCELLED = "payment.cancelled.v1";
    public static final String REFUND_REQUESTED = "payment.refund-requested.v1";

    private final PaymentRepository payments;
    private final PaymentEventRepository timeline;
    private final PaymentRefundRepository refunds;
    private final AccountRepository accounts;
    private final OutboxService outbox;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private final RateLimiter rateLimiter;

    public PaymentService(PaymentRepository payments, PaymentEventRepository timeline,
                          PaymentRefundRepository refunds, AccountRepository accounts,
                          OutboxService outbox, AuditService audit, ObjectMapper mapper,
                          TransactionTemplate tx, RateLimiter rateLimiter) {
        this.payments = payments;
        this.timeline = timeline;
        this.refunds = refunds;
        this.accounts = accounts;
        this.outbox = outbox;
        this.audit = audit;
        this.mapper = mapper;
        this.tx = tx;
        this.rateLimiter = rateLimiter;
    }

    public PaymentResponse create(Long merchantId, String idempotencyKey, Long sourceAccountId,
                                  Long destinationAccountId, long amountCents, String currency,
                                  String paymentToken, UUID correlationId) {
        validateKey(idempotencyKey);
        rateLimiter.acquire(merchantId);
        Payment existing = payments.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey).orElse(null);
        if (existing != null) {
            requireSameRequest(existing, sourceAccountId, destinationAccountId, amountCents, currency, paymentToken);
            return PaymentResponse.from(existing);
        }

        try {
            return tx.execute(status -> {
                accounts.findByIdAndCustomerId(sourceAccountId, merchantId)
                        .orElseThrow(() -> notFound("Source account not found"));
                if (!accounts.existsById(destinationAccountId)) {
                    throw notFound("Destination account not found");
                }
                if (sourceAccountId.equals(destinationAccountId)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_SAME_ACCOUNT",
                            "Source and destination accounts must differ");
                }

                Payment payment = payments.saveAndFlush(new Payment(merchantId, idempotencyKey,
                        sourceAccountId, destinationAccountId, amountCents, currency,
                        paymentToken, correlationId));
                PaymentRequested payload = new PaymentRequested(payment.getId(), merchantId, sourceAccountId,
                        destinationAccountId, amountCents, currency);
                UUID eventId = outbox.enqueue(REQUESTED, payment.getId(), correlationId, null,
                        "payment-api", payload);
                append(payment, eventId, REQUESTED, null, Payment.Status.REQUESTED,
                        "merchant:" + merchantId, Map.of("amountCents", amountCents, "currency", currency));
                audit.record("merchant:" + merchantId, "PAYMENT_CREATED", "payment",
                        payment.getId(), correlationId, Map.of("eventId", eventId));
                return PaymentResponse.from(payment);
            });
        } catch (DataIntegrityViolationException race) {
            Payment winner = payments.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey)
                    .orElseThrow(() -> race);
            requireSameRequest(winner, sourceAccountId, destinationAccountId, amountCents, currency, paymentToken);
            return PaymentResponse.from(winner);
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Long merchantId, UUID paymentId) {
        return PaymentResponse.from(owned(merchantId, paymentId));
    }

    @Transactional(readOnly = true)
    public List<TimelineResponse> timeline(Long merchantId, UUID paymentId) {
        owned(merchantId, paymentId);
        return timeline.findByPaymentIdOrderByOccurredAtAsc(paymentId).stream()
                .map(TimelineResponse::from).toList();
    }

    @Transactional
    public PaymentResponse requestCapture(Long merchantId, UUID paymentId) {
        rateLimiter.acquire(merchantId);
        Payment payment = lockedOwned(merchantId, paymentId);
        if (payment.getStatus() == Payment.Status.CAPTURE_PENDING
                || payment.getStatus() == Payment.Status.CAPTURED
                || payment.getStatus() == Payment.Status.SETTLED) {
            return PaymentResponse.from(payment);
        }
        Payment.Status old;
        try {
            old = payment.transition(Payment.Status.AUTHORIZED, Payment.Status.CAPTURE_PENDING);
        } catch (IllegalStateException e) {
            throw conflict("INVALID_PAYMENT_TRANSITION", e.getMessage());
        }
        UUID eventId = outbox.enqueue(CAPTURE_REQUESTED, paymentId, payment.getCorrelationId(), null,
                "payment-api", new PaymentReference(paymentId));
        append(payment, eventId, CAPTURE_REQUESTED, old, payment.getStatus(),
                "merchant:" + merchantId, Map.of());
        audit.record("merchant:" + merchantId, "PAYMENT_CAPTURE_REQUESTED", "payment",
                paymentId, payment.getCorrelationId(), Map.of("eventId", eventId));
        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse cancel(Long merchantId, UUID paymentId) {
        rateLimiter.acquire(merchantId);
        Payment payment = lockedOwned(merchantId, paymentId);
        if (payment.getStatus() == Payment.Status.CANCELLED) {
            return PaymentResponse.from(payment);
        }
        Payment.Status old;
        try {
            old = payment.cancel();
        } catch (IllegalStateException e) {
            throw conflict("INVALID_PAYMENT_TRANSITION", e.getMessage());
        }
        UUID eventId = outbox.enqueue(CANCELLED, paymentId, payment.getCorrelationId(), null,
                "payment-api", new PaymentReference(paymentId));
        append(payment, eventId, CANCELLED, old, payment.getStatus(),
                "merchant:" + merchantId, Map.of());
        audit.record("merchant:" + merchantId, "PAYMENT_CANCELLED", "payment",
                paymentId, payment.getCorrelationId(), Map.of("eventId", eventId));
        return PaymentResponse.from(payment);
    }

    @Transactional
    public RefundResponse requestRefund(Long merchantId, UUID paymentId, String idempotencyKey, long amountCents) {
        validateKey(idempotencyKey);
        rateLimiter.acquire(merchantId);
        Payment payment = lockedOwned(merchantId, paymentId);
        PaymentRefund existing = refunds.findByPaymentIdAndIdempotencyKey(paymentId, idempotencyKey).orElse(null);
        if (existing != null) {
            if (existing.getAmountCents() != amountCents) {
                throw conflict("IDEMPOTENCY_KEY_REUSED",
                        "Idempotency key was already used with a different refund amount");
            }
            return RefundResponse.from(existing);
        }
        if (!payment.canRefund(amountCents)) {
            throw conflict("REFUND_NOT_ALLOWED", "Refund exceeds the remaining amount or payment is not refundable");
        }
        PaymentRefund refund = refunds.save(new PaymentRefund(paymentId, idempotencyKey, amountCents));
        UUID eventId = outbox.enqueue(REFUND_REQUESTED, paymentId, payment.getCorrelationId(), null,
                "payment-api", new RefundRequested(paymentId, refund.getId(), amountCents));
        append(payment, eventId, REFUND_REQUESTED, payment.getStatus(), payment.getStatus(),
                "merchant:" + merchantId, Map.of("refundId", refund.getId(), "amountCents", amountCents));
        audit.record("merchant:" + merchantId, "PAYMENT_REFUND_REQUESTED", "refund",
                refund.getId(), payment.getCorrelationId(), Map.of("paymentId", paymentId, "eventId", eventId));
        return RefundResponse.from(refund);
    }

    public void append(Payment payment, UUID eventId, String eventType, Payment.Status from,
                       Payment.Status to, String actor, Object details) {
        try {
            timeline.save(new PaymentEvent(payment.getId(), eventId, eventType, from, to, actor,
                    mapper.writeValueAsString(details)));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Timeline details cannot be serialized", e);
        }
    }

    private Payment owned(Long merchantId, UUID paymentId) {
        return payments.findByIdAndMerchantId(paymentId, merchantId)
                .orElseThrow(() -> notFound("Payment not found"));
    }

    private Payment lockedOwned(Long merchantId, UUID paymentId) {
        Payment payment = payments.findWithLockById(paymentId)
                .orElseThrow(() -> notFound("Payment not found"));
        if (!payment.getMerchantId().equals(merchantId)) {
            throw notFound("Payment not found");
        }
        return payment;
    }

    static void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must contain 1 to 100 characters");
        }
    }

    private static void requireSameRequest(Payment existing, Long source, Long destination,
                                           long amount, String currency, String paymentToken) {
        if (!existing.getSourceAccountId().equals(source)
                || !existing.getDestinationAccountId().equals(destination)
                || existing.getAmountCents() != amount
                || !existing.getCurrency().equals(currency)
                || !existing.getPaymentToken().equals(paymentToken)) {
            throw conflict("IDEMPOTENCY_KEY_REUSED",
                    "Idempotency key was already used with a different request");
        }
    }

    private static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", message);
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
