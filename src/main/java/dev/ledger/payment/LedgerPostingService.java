package dev.ledger.payment;

import dev.ledger.account.Account;
import dev.ledger.account.AccountRepository;
import dev.ledger.account.LedgerEntry;
import dev.ledger.account.LedgerEntryRepository;
import dev.ledger.event.EventEnvelope;
import dev.ledger.event.IdempotentEventProcessor;
import dev.ledger.event.OutboxService;
import dev.ledger.payment.PaymentDtos.PaymentReference;
import dev.ledger.payment.PaymentDtos.RefundRequested;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class LedgerPostingService {

    public static final String CAPTURE_POSTED = "payment.capture-posted.v1";
    public static final String FAILED = "payment.failed.v1";
    public static final String REFUNDED = "payment.refunded.v1";
    public static final String REFUND_FAILED = "payment.refund-failed.v1";

    private final PaymentRepository payments;
    private final PaymentRefundRepository refunds;
    private final AccountRepository accounts;
    private final LedgerEntryRepository entries;
    private final PaymentLedgerPostingRepository postings;
    private final PaymentService paymentService;
    private final OutboxService outbox;
    private final IdempotentEventProcessor idempotency;
    private final MeterRegistry meters;

    public LedgerPostingService(PaymentRepository payments, PaymentRefundRepository refunds,
                                AccountRepository accounts, LedgerEntryRepository entries,
                                PaymentLedgerPostingRepository postings, PaymentService paymentService,
                                OutboxService outbox, IdempotentEventProcessor idempotency,
                                MeterRegistry meters) {
        this.payments = payments;
        this.refunds = refunds;
        this.accounts = accounts;
        this.entries = entries;
        this.postings = postings;
        this.paymentService = paymentService;
        this.outbox = outbox;
        this.idempotency = idempotency;
        this.meters = meters;
    }

    @Transactional
    public void processCapture(EventEnvelope event) {
        idempotency.process("ledger-capture", event, () -> capture(event));
    }

    @Transactional
    public void processRefund(EventEnvelope event) {
        idempotency.process("ledger-refund", event, () -> refund(event));
    }

    private void capture(EventEnvelope event) {
        UUID paymentId = UUID.fromString(event.aggregateId());
        Payment payment = payments.findWithLockById(paymentId).orElseThrow();
        if (postings.findByPaymentIdAndOperationKey(paymentId, "CAPTURE").isPresent()) {
            return;
        }
        if (payment.getStatus() != Payment.Status.CAPTURE_PENDING) {
            return;
        }

        LockedAccounts locked = lock(payment.getSourceAccountId(), payment.getDestinationAccountId());
        Account source = locked.account(payment.getSourceAccountId());
        Account destination = locked.account(payment.getDestinationAccountId());
        String failure = validate(source, destination, payment.getAmountCents());
        if (failure != null) {
            Payment.Status old = payment.transition(Payment.Status.CAPTURE_PENDING, Payment.Status.FAILED);
            UUID failedEvent = outbox.enqueue(FAILED, paymentId, payment.getCorrelationId(), event.eventId(),
                    "paymentops", Map.of("paymentId", paymentId, "reason", failure));
            paymentService.append(payment, failedEvent, FAILED, old, payment.getStatus(),
                    "paymentops", Map.of("reason", failure));
            meters.counter("paymentops.ledger.postings", "result", "failed").increment();
            return;
        }

        source.debit(payment.getAmountCents());
        destination.credit(payment.getAmountCents());
        LedgerEntry debit = entries.save(new LedgerEntry(source.getId(), LedgerEntry.Direction.DEBIT,
                payment.getAmountCents(), null, paymentId, event.eventId()));
        LedgerEntry credit = entries.save(new LedgerEntry(destination.getId(), LedgerEntry.Direction.CREDIT,
                payment.getAmountCents(), null, paymentId, event.eventId()));
        entries.flush();
        postings.save(new PaymentLedgerPosting(paymentId, "CAPTURE", event.eventId(),
                debit.getId(), credit.getId(), payment.getAmountCents()));
        Payment.Status old = payment.transition(Payment.Status.CAPTURE_PENDING, Payment.Status.CAPTURED);
        UUID postedEvent = outbox.enqueue(CAPTURE_POSTED, paymentId, payment.getCorrelationId(), event.eventId(),
                "paymentops", new PaymentReference(paymentId));
        paymentService.append(payment, postedEvent, CAPTURE_POSTED, old, payment.getStatus(),
                "paymentops", Map.of("debitEntryId", debit.getId(), "creditEntryId", credit.getId()));
        meters.counter("paymentops.ledger.postings", "result", "posted").increment();
    }

    private void refund(EventEnvelope event) {
        RefundRequested request = new RefundRequested(
                UUID.fromString(event.payload().path("paymentId").asText()),
                UUID.fromString(event.payload().path("refundId").asText()),
                event.payload().path("amountCents").asLong());
        PaymentRefund refund = refunds.findById(request.refundId()).orElseThrow();
        if (refund.getStatus() != PaymentRefund.Status.REQUESTED) {
            return;
        }
        Payment payment = payments.findWithLockById(request.paymentId()).orElseThrow();
        String operation = "REFUND:" + refund.getId();
        if (postings.findByPaymentIdAndOperationKey(payment.getId(), operation).isPresent()) {
            return;
        }

        LockedAccounts locked = lock(payment.getSourceAccountId(), payment.getDestinationAccountId());
        Account merchantDestination = locked.account(payment.getDestinationAccountId());
        Account originalSource = locked.account(payment.getSourceAccountId());
        String failure = validate(merchantDestination, originalSource, refund.getAmountCents());
        if (failure != null || !payment.canRefund(refund.getAmountCents())) {
            String reason = failure == null ? "Refund exceeds remaining payment amount" : failure;
            refund.reject(reason);
            UUID failedEvent = outbox.enqueue(REFUND_FAILED, payment.getId(), payment.getCorrelationId(),
                    event.eventId(), "paymentops", Map.of("paymentId", payment.getId(),
                            "refundId", refund.getId(), "reason", reason));
            paymentService.append(payment, failedEvent, REFUND_FAILED, payment.getStatus(), payment.getStatus(),
                    "paymentops", Map.of("refundId", refund.getId(), "reason", reason));
            return;
        }

        merchantDestination.debit(refund.getAmountCents());
        originalSource.credit(refund.getAmountCents());
        LedgerEntry debit = entries.save(new LedgerEntry(merchantDestination.getId(), LedgerEntry.Direction.DEBIT,
                refund.getAmountCents(), null, payment.getId(), event.eventId()));
        LedgerEntry credit = entries.save(new LedgerEntry(originalSource.getId(), LedgerEntry.Direction.CREDIT,
                refund.getAmountCents(), null, payment.getId(), event.eventId()));
        entries.flush();
        postings.save(new PaymentLedgerPosting(payment.getId(), operation, event.eventId(),
                debit.getId(), credit.getId(), refund.getAmountCents()));
        Payment.Status old = payment.getStatus();
        payment.completeRefund(refund.getAmountCents());
        refund.complete();
        UUID refundedEvent = outbox.enqueue(REFUNDED, payment.getId(), payment.getCorrelationId(),
                event.eventId(), "paymentops", Map.of("paymentId", payment.getId(), "refundId", refund.getId()));
        paymentService.append(payment, refundedEvent, REFUNDED, old, payment.getStatus(),
                "paymentops", Map.of("refundId", refund.getId(), "amountCents", refund.getAmountCents()));
        meters.counter("paymentops.ledger.postings", "result", "refunded").increment();
    }

    private LockedAccounts lock(Long firstAccountId, Long secondAccountId) {
        long lowId = Math.min(firstAccountId, secondAccountId);
        long highId = Math.max(firstAccountId, secondAccountId);
        Account low = accounts.findWithLockById(lowId).orElseThrow();
        Account high = accounts.findWithLockById(highId).orElseThrow();
        return new LockedAccounts(low, high);
    }

    private static String validate(Account debit, Account credit, long amount) {
        if (debit.getStatus() != Account.Status.OPEN) {
            return "Debit account is " + debit.getStatus();
        }
        if (credit.getStatus() != Account.Status.OPEN) {
            return "Credit account is " + credit.getStatus();
        }
        if (debit.getBalanceCents() < amount) {
            return "Insufficient funds";
        }
        return null;
    }

    private record LockedAccounts(Account low, Account high) {
        Account account(Long id) {
            return low.getId().equals(id) ? low : high;
        }
    }
}
