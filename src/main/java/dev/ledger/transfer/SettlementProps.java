package dev.ledger.transfer;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ledger.settlement")
public record SettlementProps(Mode mode, String queue) {

    public enum Mode {SYNC, ASYNC}

    public boolean isAsync() {
        return mode == Mode.ASYNC;
    }
}
