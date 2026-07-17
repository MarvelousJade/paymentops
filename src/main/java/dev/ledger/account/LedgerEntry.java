package dev.ledger.account;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

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

    private Instant createdAt = Instant.now();

    protected LedgerEntry() {
    }

    public LedgerEntry(Long accountId, Direction direction, long amountCents, Long transferId) {
        this.accountId = accountId;
        this.direction = direction;
        this.amountCents = amountCents;
        this.transferId = transferId;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
