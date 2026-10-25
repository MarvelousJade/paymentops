package dev.ledger.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByMerchantIdAndIdempotencyKey(Long merchantId, String idempotencyKey);

    Optional<Payment> findByIdAndMerchantId(UUID id, Long merchantId);

    List<Payment> findByCorrelationIdOrderByCreatedAt(UUID correlationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findWithLockById(@Param("id") UUID id);

    @Query("""
            select coalesce(sum(p.amountCents), 0) from Payment p
            where p.merchantId = :merchantId and p.createdAt >= :since
            and p.status not in :excluded
            """)
    long sumActiveAmountSince(@Param("merchantId") Long merchantId, @Param("since") Instant since,
                              @Param("excluded") Set<Payment.Status> excluded);

    long countByMerchantIdAndCreatedAtAfter(Long merchantId, Instant since);

    List<Payment> findByStatusAndUpdatedAtBefore(Payment.Status status, Instant before);
}
