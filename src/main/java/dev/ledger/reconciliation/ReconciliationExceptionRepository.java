package dev.ledger.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReconciliationExceptionRepository extends JpaRepository<ReconciliationException, UUID> {
    Optional<ReconciliationException> findByPaymentIdAndClassification(
            UUID paymentId, ReconciliationException.Classification classification);
    List<ReconciliationException> findByPaymentId(UUID paymentId);
    List<ReconciliationException> findByStatusOrderByFirstDetectedAt(
            ReconciliationException.Status status);
    long countByStatus(ReconciliationException.Status status);
    boolean existsByClassificationAndEvidence(
            ReconciliationException.Classification classification, String evidence);
}
