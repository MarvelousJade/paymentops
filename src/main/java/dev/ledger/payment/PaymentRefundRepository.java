package dev.ledger.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, UUID> {
    Optional<PaymentRefund> findByPaymentIdAndIdempotencyKey(UUID paymentId, String idempotencyKey);
}
