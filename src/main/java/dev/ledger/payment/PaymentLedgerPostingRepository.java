package dev.ledger.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentLedgerPostingRepository extends JpaRepository<PaymentLedgerPosting, UUID> {
    Optional<PaymentLedgerPosting> findByPaymentIdAndOperationKey(UUID paymentId, String operationKey);
    List<PaymentLedgerPosting> findByPaymentIdOrderByPostedAt(UUID paymentId);
    long countByPaymentId(UUID paymentId);
}
