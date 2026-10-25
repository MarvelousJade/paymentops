package dev.ledger.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID correlationId,
        UUID causationId,
        String aggregateId,
        int schemaVersion,
        String producer,
        JsonNode payload) {

    public static EventEnvelope from(OutboxEvent event, ObjectMapper mapper) {
        try {
            return new EventEnvelope(event.getEventId(), event.getEventType(), event.getOccurredAt(),
                    event.getCorrelationId(), event.getCausationId(), event.getAggregateId(),
                    event.getSchemaVersion(), event.getProducer(), mapper.readTree(event.getPayload()));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid outbox payload for " + event.getEventId(), e);
        }
    }
}
