package dev.ledger.common;

import org.slf4j.MDC;

import java.util.UUID;

public final class CorrelationIds {

    public static final String HEADER = "X-Correlation-ID";
    private static final String MDC_KEY = "correlationId";

    private CorrelationIds() {
    }

    public static UUID current() {
        String value = MDC.get(MDC_KEY);
        if (value == null) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return UUID.randomUUID();
        }
    }

    static void set(UUID id) {
        MDC.put(MDC_KEY, id.toString());
    }

    static void clear() {
        MDC.remove(MDC_KEY);
    }
}
