package dev.ledger.payment;

import dev.ledger.common.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record CreatePaymentRequest(
            @NotNull Long sourceAccountId,
            @NotNull Long destinationAccountId,
            @NotBlank String amount,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotBlank @Pattern(regexp = "tok_[A-Za-z0-9_-]{8,100}") String paymentToken) {
    }

    public record RefundRequest(@NotBlank String amount) {
    }

    public record ResolutionRequest(@NotBlank @Size(max = 500) String notes) {
    }

    public record PaymentResponse(
            UUID id, Long sourceAccountId, Long destinationAccountId, String amount,
            String refundedAmount, String currency, Payment.Status status,
            UUID correlationId, Instant createdAt, Instant updatedAt) {

        public static PaymentResponse from(Payment payment) {
            return new PaymentResponse(payment.getId(), payment.getSourceAccountId(),
                    payment.getDestinationAccountId(), Money.toDecimalString(payment.getAmountCents()),
                    Money.toDecimalString(payment.getRefundedAmountCents()), payment.getCurrency(),
                    payment.getStatus(), payment.getCorrelationId(), payment.getCreatedAt(), payment.getUpdatedAt());
        }
    }

    public record RefundResponse(UUID id, UUID paymentId, String amount, PaymentRefund.Status status,
                                 String failureReason, Instant createdAt, Instant completedAt) {
        public static RefundResponse from(PaymentRefund refund) {
            return new RefundResponse(refund.getId(), refund.getPaymentId(),
                    Money.toDecimalString(refund.getAmountCents()), refund.getStatus(),
                    refund.getFailureReason(), refund.getCreatedAt(), refund.getCompletedAt());
        }
    }

    public record TimelineResponse(UUID eventId, String eventType, String fromStatus, String toStatus,
                                   String actor, String details, Instant occurredAt) {
        public static TimelineResponse from(PaymentEvent event) {
            return new TimelineResponse(event.getEventId(), event.getEventType(), event.getFromStatus(),
                    event.getToStatus(), event.getActor(), event.getDetails(), event.getOccurredAt());
        }
    }

    public record PaymentRequested(UUID paymentId, Long merchantId, Long sourceAccountId,
                                   Long destinationAccountId, long amountCents, String currency) {
    }

    public record PaymentReference(UUID paymentId) {
    }

    public record RefundRequested(UUID paymentId, UUID refundId, long amountCents) {
    }
}
