package dev.ledger.account;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account {

    public enum Type {CHEQUING, SAVINGS}

    public enum Status {OPEN, FROZEN, CLOSED}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long customerId;

    @Enumerated(EnumType.STRING)
    private Type type;

    @Enumerated(EnumType.STRING)
    private Status status = Status.OPEN;

    private long balanceCents;

    @Version
    private long version;

    private Instant createdAt = Instant.now();

    protected Account() {
    }

    public Account(Long customerId, Type type) {
        this.customerId = customerId;
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Type getType() {
        return type;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public long getBalanceCents() {
        return balanceCents;
    }

    public void credit(long cents) {
        this.balanceCents += cents;
    }

    public void debit(long cents) {
        this.balanceCents -= cents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
