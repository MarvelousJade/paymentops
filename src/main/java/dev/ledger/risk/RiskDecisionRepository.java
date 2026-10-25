package dev.ledger.risk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RiskDecisionRepository extends JpaRepository<RiskDecision, UUID> {
    Optional<RiskDecision> findByPaymentId(UUID paymentId);
}
