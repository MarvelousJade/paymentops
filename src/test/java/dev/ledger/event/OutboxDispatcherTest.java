package dev.ledger.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxDispatcherTest {
    @Test
    void failedSendRemainsPendingWithBackoffAndCanBeRetried() {
        OutboxEvent event = event();
        OutboxEventRepository repository = repository(event);
        EventTransport transport = mock(EventTransport.class);
        doThrow(new IllegalStateException("synthetic unavailable transport"))
                .doNothing().when(transport).send(any());
        OutboxDispatcher dispatcher = dispatcher(repository, transport, 3);
        Instant before = Instant.now();
        dispatcher.dispatch();
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getNextAttemptAt()).isAfter(before);
        assertThat(event.getLastError()).contains("unavailable transport");
        verify(repository).save(event);
        // Repository fixture supplies the now-due event again, without a real sleep.
        dispatcher.dispatch();
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getLastError()).isNull();
        verify(transport, times(2)).send(any());
    }

    @Test
    void repeatedFailureReachesDeadLetterAtConfiguredLimit() {
        OutboxEvent event = event();
        OutboxEventRepository repository = repository(event);
        EventTransport transport = envelope -> { throw new IllegalStateException("still unavailable"); };
        OutboxDispatcher dispatcher = dispatcher(repository, transport, 3);
        dispatcher.dispatch();
        dispatcher.dispatch();
        dispatcher.dispatch();
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.DEAD_LETTER);
        assertThat(event.getAttempts()).isEqualTo(3);
        assertThat(event.getPublishedAt()).isNull();
        event.replay();
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getLastError()).isNull();
    }

    @Test
    void successfulSendMarksPublishedOnlyAfterTransportAcknowledgement() {
        OutboxEvent event = event();
        OutboxEventRepository repository = repository(event);
        dispatcher(repository, envelope -> {
            assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
            assertThat(event.getPublishedAt()).isNull();
        }, 3).dispatch();
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PUBLISHED);
        assertThat(event.getAttempts()).isZero();
        verify(repository).save(event);
    }

    private OutboxEvent event() {
        return new OutboxEvent(UUID.randomUUID(), "test.event", UUID.randomUUID().toString(),
                UUID.randomUUID(), null, "test", "{}");
    }

    private OutboxEventRepository repository(OutboxEvent event) {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        when(repository.findDue(eq(OutboxEvent.Status.PENDING), any(), any())).thenReturn(List.of(event));
        return repository;
    }

    private OutboxDispatcher dispatcher(OutboxEventRepository repository, EventTransport transport, int attempts) {
        return new OutboxDispatcher(repository, transport, new ObjectMapper(), new SimpleMeterRegistry(), attempts);
    }
}
