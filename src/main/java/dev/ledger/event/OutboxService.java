package dev.ledger.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OutboxService {

    private final OutboxEventRepository events;
    private final ObjectMapper mapper;

    public OutboxService(OutboxEventRepository events, ObjectMapper mapper) {
        this.events = events;
        this.mapper = mapper;
    }

    public UUID enqueue(String eventType, Object aggregateId, UUID correlationId,
                        UUID causationId, String producer, Object payload) {
        UUID eventId = UUID.randomUUID();
        try {
            events.save(new OutboxEvent(eventId, eventType, aggregateId.toString(), correlationId,
                    causationId, producer, mapper.writeValueAsString(payload)));
            return eventId;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Event payload cannot be serialized", e);
        }
    }
}
