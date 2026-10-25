package dev.ledger.event;

import dev.ledger.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "paymentops.messaging.mode=kafka",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "spring.kafka.admin.operation-timeout=1s",
        "paymentops.outbox.poll-delay-ms=3600000",
        "paymentops.webhook.poll-delay-ms=3600000"})
@Import(TestcontainersConfiguration.class)
class KafkaConfigurationTest {

    @Autowired
    EventTransport transport;

    @Autowired
    KafkaEventConsumer consumer;

    @MockitoSpyBean
    OutboxDispatcher dispatcher;

    @Test
    void kafkaTransportRetryAndDeadLetterConfigurationLoads() {
        assertThat(transport).isInstanceOf(KafkaEventTransport.class);
        assertThat(consumer).isNotNull();
        verify(dispatcher, never()).dispatch();
    }
}
