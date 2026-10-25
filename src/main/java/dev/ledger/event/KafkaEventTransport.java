package dev.ledger.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "paymentops.messaging.mode", havingValue = "kafka")
public class KafkaEventTransport implements EventTransport {

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    public KafkaEventTransport(KafkaTemplate<String, String> kafka, ObjectMapper mapper) {
        this.kafka = kafka;
        this.mapper = mapper;
    }

    @Override
    public void send(EventEnvelope event) {
        try {
            kafka.send(event.eventType(), event.aggregateId(), mapper.writeValueAsString(event))
                    .get(10, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Event envelope cannot be serialized", e);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Kafka publish failed", e);
        }
    }
}
