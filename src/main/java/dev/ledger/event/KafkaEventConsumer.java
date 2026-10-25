package dev.ledger.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "paymentops.messaging.mode", havingValue = "kafka")
public class KafkaEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventConsumer.class);

    private final EventRouter router;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;

    public KafkaEventConsumer(EventRouter router, ObjectMapper mapper, MeterRegistry meters) {
        this.router = router;
        this.mapper = mapper;
        this.meters = meters;
    }

    @RetryableTopic(
            attempts = "4",
            retryTopicSuffix = ".retry",
            dltTopicSuffix = ".dead-letter",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(topics = {
            "payment.requested.v1",
            "payment.risk-decided.v1",
            "payment.approved.v1",
            "payment.rejected.v1",
            "payment.capture-requested.v1",
            "payment.capture-posted.v1",
            "payment.cancelled.v1",
            "payment.failed.v1",
            "payment.settled.v1",
            "payment.settlement-delayed.v1",
            "payment.settlement-failed.v1",
            "payment.refund-requested.v1",
            "payment.refunded.v1",
            "payment.refund-failed.v1",
            "reconciliation.mismatch.v1"
    }, groupId = "paymentops-processors")
    public void consume(String value) throws Exception {
        router.route(mapper.readValue(value, EventEnvelope.class));
    }

    @DltHandler
    public void deadLetter(String value, ConsumerRecord<?, ?> record) {
        meters.counter("paymentops.events.dead_letter", "topic", record.topic()).increment();
        log.error("Poison event moved to dead letter topic={} partition={} offset={}",
                record.topic(), record.partition(), record.offset());
    }
}
