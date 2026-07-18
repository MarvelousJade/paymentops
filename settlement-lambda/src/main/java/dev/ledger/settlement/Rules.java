package dev.ledger.settlement;

import java.util.Optional;

/** Pure limit/velocity decisions. Counters are supplied post-increment by the caller. */
public final class Rules {

    public record Limits(long maxSingleCents, long dailyLimitCents, int perMinuteLimit) {
    }

    private Rules() {
    }

    /**
     * @param amountCents      transfer amount
     * @param dailyTotalCents  from-account's total for the day, already including this transfer
     * @param minuteCount      from-account's transfer count this minute, already including this one
     * @return rejection reason, or empty to approve
     */
    public static Optional<String> check(long amountCents, long dailyTotalCents, int minuteCount, Limits limits) {
        if (amountCents > limits.maxSingleCents()) {
            return Optional.of("Exceeds single-transfer limit");
        }
        if (dailyTotalCents > limits.dailyLimitCents()) {
            return Optional.of("Daily transfer limit exceeded");
        }
        if (minuteCount > limits.perMinuteLimit()) {
            return Optional.of("Too many transfers per minute");
        }
        return Optional.empty();
    }
}
