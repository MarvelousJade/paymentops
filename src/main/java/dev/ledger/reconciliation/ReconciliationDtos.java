package dev.ledger.reconciliation;

import dev.ledger.common.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class ReconciliationDtos {

    private ReconciliationDtos() {
    }

    public record ActionRequest(@NotBlank @Size(max = 500) String notes) {
    }

    public record ExceptionResponse(UUID id, UUID paymentId,
                                    ReconciliationException.Classification classification,
                                    ReconciliationException.Status status, String expectedAmount,
                                    String actualAmount, String evidence, Instant firstDetectedAt,
                                    Instant lastDetectedAt, Instant resolvedAt) {
        static ExceptionResponse from(ReconciliationException exception) {
            return new ExceptionResponse(exception.getId(), exception.getPaymentId(),
                    exception.getClassification(), exception.getStatus(), money(exception.getExpectedAmount()),
                    money(exception.getActualAmount()), exception.getEvidence(),
                    exception.getFirstDetectedAt(), exception.getLastDetectedAt(), exception.getResolvedAt());
        }

        private static String money(Long cents) {
            return cents == null ? null : Money.toDecimalString(cents);
        }
    }

    public record RunResponse(int paymentsChecked, int openExceptions) {
    }
}
