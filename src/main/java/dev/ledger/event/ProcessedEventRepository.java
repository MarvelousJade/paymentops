package dev.ledger.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEvent.Key> {
    @Modifying
    @Query(value = """
            INSERT INTO processed_events (consumer_name, event_id)
            VALUES (:consumer, :eventId)
            ON CONFLICT (consumer_name, event_id) DO NOTHING
            """, nativeQuery = true)
    int claim(@Param("consumer") String consumer, @Param("eventId") UUID eventId);
}
