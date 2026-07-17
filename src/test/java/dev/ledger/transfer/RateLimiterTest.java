package dev.ledger.transfer;

import dev.ledger.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    @Test
    void allowsUpToLimitThenRejects() {
        RateLimiter limiter = new RateLimiter(2);

        assertThatCode(() -> {
            limiter.acquire(1L);
            limiter.acquire(1L);
        }).doesNotThrowAnyException();

        assertThatThrownBy(() -> limiter.acquire(1L))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

        // Other customers have their own bucket.
        assertThatCode(() -> limiter.acquire(2L)).doesNotThrowAnyException();
    }
}
