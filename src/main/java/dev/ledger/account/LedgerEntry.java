package dev.ledger.account;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Immutable, append-only. No setters by design. */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    public enum Direction {DEBIT, CREDIT}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long accountId;

    @Enumerated(EnumType.STRING)
    private Direction direction;

    private long amountCents;

    private Long transferId;

    private UUID paymentId;

    private UUID sourceEventId;

    private Instant createdAt = Instant.now();

    protected LedgerEntry() {
    }

    public LedgerEntry(Long accountId, Direction direction, long amountCents, Long transferId) {
        this(accountId, direction, amountCents, transferId, null, null);
    }

    public LedgerEntry(Long accountId, Direction direction, long amountCents, Long transferId,
                       UUID paymentId, UUID sourceEventId) {
        this.accountId = accountId;
        this.direction = direction;
        this.amountCents = amountCents;
        this.transferId = transferId;
        this.paymentId = paymentId;
        this.sourceEventId = sourceEventId;
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Direction getDirection() {
        return direction;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public Long getTransferId() {
        return transferId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
