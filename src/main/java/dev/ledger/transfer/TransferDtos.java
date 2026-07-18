package dev.ledger.transfer;

import dev.ledger.common.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class TransferDtos {

    private TransferDtos() {
    }

    /** SQS payload; the Lambda runs rules on it without touching the API database. */
    public record SettlementMessage(Long transferId, Long customerId, Long fromAccountId,
                                    Long toAccountId, long amountCents) {
    }

    public record TransferRequest(
            @NotNull Long fromAccountId,
            @NotNull Long toAccountId,
            @NotBlank String amount) {
    }

    public record TransferResponse(Long id, Long fromAccountId, Long toAccountId, String amount,
                                   Transfer.Status status, String failureReason,
                                   Instant createdAt, Instant completedAt) {

        static TransferResponse from(Transfer transfer) {
            return new TransferResponse(transfer.getId(), transfer.getFromAccountId(), transfer.getToAccountId(),
                    Money.toDecimalString(transfer.getAmountCents()), transfer.getStatus(),
                    transfer.getFailureReason(), transfer.getCreatedAt(), transfer.getCompletedAt());
        }
    }
}
