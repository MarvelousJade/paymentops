package dev.ledger.settlement;

import dev.ledger.common.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

public final class SettlementDtos {

    private SettlementDtos() {
    }

    public record ImportSettlementRequest(
            UUID paymentId,
            @NotBlank String externalReference,
            @NotBlank String amount,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotNull SettlementRecord.Status status) {
    }

    public record SettlementResponse(UUID id, UUID paymentId, String externalReference, String amount,
                                     String currency, SettlementRecord.Status status,
                                     Instant settledAt, Instant receivedAt) {
        public static SettlementResponse from(SettlementRecord record) {
            return new SettlementResponse(record.getId(), record.getPaymentId(), record.getExternalReference(),
                    Money.toDecimalString(record.getAmountCents()), record.getCurrency(), record.getStatus(),
                    record.getSettledAt(), record.getReceivedAt());
        }
    }
}
