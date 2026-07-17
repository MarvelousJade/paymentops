package dev.ledger.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void parsesDecimalStrings() {
        assertThat(Money.toCents("12.34")).isEqualTo(1234);
        assertThat(Money.toCents("0.5")).isEqualTo(50);
        assertThat(Money.toCents("10")).isEqualTo(1000);
    }

    @Test
    void formatsCents() {
        assertThat(Money.toDecimalString(5)).isEqualTo("0.05");
        assertThat(Money.toDecimalString(1234)).isEqualTo("12.34");
        assertThat(Money.toDecimalString(0)).isEqualTo("0.00");
    }

    @Test
    void roundTrips() {
        assertThat(Money.toDecimalString(Money.toCents("99999999.99"))).isEqualTo("99999999.99");
    }

    @Test
    void rejectsBadAmounts() {
        assertThatThrownBy(() -> Money.toCents("10.123")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Money.toCents("-5")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Money.toCents("0")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Money.toCents("abc")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Money.toCents("999999999999999999999")).isInstanceOf(ApiException.class);
    }
}
