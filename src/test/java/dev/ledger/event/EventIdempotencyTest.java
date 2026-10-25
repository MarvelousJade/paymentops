package dev.ledger.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ledger.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"paymentops.outbox.poll-delay-ms=3600000",
        "paymentops.webhook.poll-delay-ms=3600000"})
@Import(TestcontainersConfiguration.class)
class EventIdempotencyTest {
    @Autowired IdempotentEventProcessor processor;
    @Autowired ProcessedEventRepository processed;
    @Autowired OutboxService outbox;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void concurrentDeliveryCommitsOneEffectAndReturnsDuplicateWithoutTransactionFailure() throws Exception {
        EventEnvelope event = event();
        CountDownLatch effectStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> processor.process("race-test", event, () -> {
                effectStarted.countDown();
                await(release);
                effect(event);
            }));
            assertThat(effectStarted.await(10, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> processor.process("race-test", event, () -> effect(event)));
            try {
                // Observe the real PostgreSQL lock wait, rather than rely on a timing-only race.
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                boolean waiting = false;
                while (System.nanoTime() < deadline && !waiting) {
                    waiting = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity "
                            + "WHERE datname=current_database() AND wait_event_type='Lock' "
                            + "AND query ILIKE '%processed_events%'", Long.class) > 0;
                    if (!waiting) Thread.sleep(20);
                }
                assertThat(waiting).as("second delivery waiting on the uncommitted event claim").isTrue();
            } finally {
                release.countDown();
            }
            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(10, TimeUnit.SECONDS)).isFalse();
        } finally {
            release.countDown();
        }
        assertThat(effectCount(event)).isEqualTo(1);
    }

    @Test
    void failedEffectRollsBackClaimAndOutgoingEventThenRetrySucceeds() {
        EventEnvelope event = event();
        assertThatThrownBy(() -> processor.process("rollback-test", event, () -> {
            effect(event);
            throw new IllegalStateException("synthetic downstream failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(processed.existsById(new ProcessedEvent.Key("rollback-test", event.eventId()))).isFalse();
        assertThat(effectCount(event)).isZero();
        assertThat(processor.process("rollback-test", event, () -> effect(event))).isTrue();
        assertThat(processor.process("rollback-test", event, () -> effect(event))).isFalse();
        assertThat(effectCount(event)).isEqualTo(1);
    }

    private EventEnvelope event() {
        UUID id = UUID.randomUUID();
        return new EventEnvelope(id, "test.requested", Instant.now(), UUID.randomUUID(), null,
                id.toString(), 1, "test", mapper.createObjectNode());
    }

    private void effect(EventEnvelope event) {
        outbox.enqueue("test.effect", event.eventId(), event.correlationId(), event.eventId(), "test", Map.of());
    }

    private long effectCount(EventEnvelope event) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_type='test.effect' "
                + "AND aggregate_id=?", Long.class, event.eventId().toString());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Latch timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
