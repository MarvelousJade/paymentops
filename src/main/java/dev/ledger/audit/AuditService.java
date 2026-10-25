package dev.ledger.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuditService {

    private final AuditEventRepository events;
    private final ObjectMapper mapper;

    public AuditService(AuditEventRepository events, ObjectMapper mapper) {
        this.events = events;
        this.mapper = mapper;
    }

    public void record(String actor, String action, String aggregateType, Object aggregateId,
                       UUID correlationId, Object details) {
        try {
            events.save(new AuditEvent(actor, action, aggregateType, aggregateId.toString(),
                    correlationId, mapper.writeValueAsString(details)));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Audit details cannot be serialized", e);
        }
    }
}
