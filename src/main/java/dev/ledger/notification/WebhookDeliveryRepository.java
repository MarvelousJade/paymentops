package dev.ledger.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {
    List<WebhookDelivery> findByStatusInAndNextAttemptAtBeforeOrderByCreatedAt(
            Collection<WebhookDelivery.Status> statuses, Instant now, Pageable pageable);
    List<WebhookDelivery> findByPaymentIdOrderByCreatedAt(UUID paymentId);
}
