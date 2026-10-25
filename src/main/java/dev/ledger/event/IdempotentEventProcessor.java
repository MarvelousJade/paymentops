package dev.ledger.event;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        // PostgreSQL waits for an in-flight conflicting claim. Unlike catching a
        // unique-constraint exception, DO NOTHING keeps the transaction usable.
        // The claim still rolls back with the effect if processing fails.
        if (processed.claim(consumerName, event.eventId()) == 0) {
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
