package dev.ledger.event;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("""
            select e from OutboxEvent e
            where e.status = :status and e.nextAttemptAt <= :now
            order by e.occurredAt
            """)
    List<OutboxEvent> findDue(@Param("status") OutboxEvent.Status status,
                              @Param("now") Instant now, Pageable pageable);

    Optional<OutboxEvent> findByEventIdAndStatus(UUID eventId, OutboxEvent.Status status);

    long countByStatus(OutboxEvent.Status status);
}
