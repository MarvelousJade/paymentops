package dev.ledger.settlement;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RulesTest {

    private final Rules.Limits limits = new Rules.Limits(100_000, 500_000, 5);

    @Test
    void approvesWithinAllLimits() {
        assertThat(Rules.check(50_000, 200_000, 3, limits)).isEmpty();
    }

    @Test
    void rejectsOverSingleLimit() {
        assertThat(Rules.check(100_001, 100_001, 1, limits)).contains("Exceeds single-transfer limit");
    }

    @Test
    void rejectsOverDailyLimit() {
        assertThat(Rules.check(50_000, 500_001, 1, limits)).contains("Daily transfer limit exceeded");
    }

    @Test
    void rejectsOverPerMinuteLimit() {
        assertThat(Rules.check(1_000, 10_000, 6, limits)).contains("Too many transfers per minute");
    }

    @Test
    void boundaryValuesPass() {
        assertThat(Rules.check(100_000, 500_000, 5, limits)).isEmpty();
    }
}
