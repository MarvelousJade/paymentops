package dev.ledger.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationActionRepository extends JpaRepository<ReconciliationAction, Long> {
}
