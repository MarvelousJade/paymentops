package dev.ledger.account;

import dev.ledger.common.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record CreateAccountRequest(@NotNull Account.Type type) {
    }

    public record DepositRequest(@NotBlank String amount) {
    }

    public record StatusRequest(@NotNull Account.Status status) {
    }

    public record AccountResponse(Long id, Account.Type type, Account.Status status,
                                  String balance, Instant createdAt) {

        static AccountResponse from(Account account) {
            return new AccountResponse(account.getId(), account.getType(), account.getStatus(),
                    Money.toDecimalString(account.getBalanceCents()), account.getCreatedAt());
        }
    }

    public record LedgerEntryResponse(Long id, LedgerEntry.Direction direction, String amount,
                                      Long transferId, Instant createdAt) {

        static LedgerEntryResponse from(LedgerEntry entry) {
            return new LedgerEntryResponse(entry.getId(), entry.getDirection(),
                    Money.toDecimalString(entry.getAmountCents()), entry.getTransferId(), entry.getCreatedAt());
        }
    }
}
