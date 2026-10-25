package dev.ledger.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxDispatcher {

    private final OutboxEventRepository events;
    private final EventTransport transport;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;
    private final int maxAttempts;

    public OutboxDispatcher(OutboxEventRepository events, EventTransport transport,
                            ObjectMapper mapper, MeterRegistry meters,
                            @Value("${paymentops.outbox.max-attempts:5}") int maxAttempts) {
        this.events = events;
        this.transport = transport;
        this.mapper = mapper;
        this.meters = meters;
        this.maxAttempts = maxAttempts;
        meters.gauge("paymentops.outbox.pending", events,
                repository -> repository.countByStatus(OutboxEvent.Status.PENDING));
    }

    @Scheduled(fixedDelayString = "${paymentops.outbox.poll-delay-ms:250}",
            initialDelayString = "${paymentops.outbox.initial-delay-ms:0}")
    public void dispatch() {
        List<OutboxEvent> due = events.findDue(
                OutboxEvent.Status.PENDING, Instant.now(), PageRequest.of(0, 50));
        for (OutboxEvent event : due) {
            try {
                transport.send(EventEnvelope.from(event, mapper));
                event.published();
                meters.counter("paymentops.outbox.published", "event_type", event.getEventType()).increment();
            } catch (Exception failure) {
                event.failed(failure, maxAttempts);
                meters.counter("paymentops.outbox.failures", "event_type", event.getEventType()).increment();
            }
            events.save(event);
        }
    }
}
