package dev.ledger.event;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotentEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(IdempotentEventProcessor.class);

    private final ProcessedEventRepository processed;
    private final MeterRegistry meters;

    public IdempotentEventProcessor(ProcessedEventRepository processed, MeterRegistry meters) {
        this.processed = processed;
        this.meters = meters;
    }

    @Transactional
    public boolean process(String consumerName, EventEnvelope event, Runnable effect) {
        ProcessedEvent.Key key = new ProcessedEvent.Key(consumerName, event.eventId());
        if (processed.existsById(key)) {
            duplicate(consumerName, event);
            return false;
        }
        try {
            processed.saveAndFlush(new ProcessedEvent(consumerName, event.eventId()));
        } catch (DataIntegrityViolationException race) {
            duplicate(consumerName, event);
            return false;
        }
        effect.run();
        return true;
    }

    private void duplicate(String consumerName, EventEnvelope event) {
        meters.counter("paymentops.events.duplicate", "consumer", consumerName).increment();
        log.warn("Duplicate event ignored consumer={} eventId={} correlationId={}",
                consumerName, event.eventId(), event.correlationId());
    }
}
