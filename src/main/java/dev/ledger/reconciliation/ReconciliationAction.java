package dev.ledger.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_actions")
public class ReconciliationAction {

    public enum Action {DETECTED, ACKNOWLEDGED, RESOLVED, REOPENED}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID exceptionId;
    private String action;
    private String actor;
    private String notes;
    @Column(insertable = false, updatable = false)
    private Instant occurredAt;

    protected ReconciliationAction() {
    }

    public ReconciliationAction(UUID exceptionId, Action action, String actor, String notes) {
        this.exceptionId = exceptionId;
        this.action = action.name();
        this.actor = actor;
        this.notes = notes;
    }
}
