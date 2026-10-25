package dev.ledger.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "paymentops.messaging.mode", havingValue = "local", matchIfMissing = true)
public class LocalEventTransport implements EventTransport {

    private final EventRouter router;

    public LocalEventTransport(EventRouter router) {
        this.router = router;
    }

    @Override
    public void send(EventEnvelope event) {
        router.route(event);
    }
}
