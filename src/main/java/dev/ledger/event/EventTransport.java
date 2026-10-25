package dev.ledger.event;

public interface EventTransport {
    void send(EventEnvelope event);
}
