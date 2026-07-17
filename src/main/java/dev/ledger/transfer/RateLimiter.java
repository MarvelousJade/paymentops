package dev.ledger.transfer;

import dev.ledger.common.ApiException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/** ponytail: in-memory per-customer buckets; swap for bucket4j-redis when running more than one instance. */
@Component
public class RateLimiter {

    private final ConcurrentHashMap<Long, Bucket> buckets = new ConcurrentHashMap<>();
    private final int perMinute;

    public RateLimiter(@Value("${ledger.rate-limit.transfers-per-minute:60}") int perMinute) {
        this.perMinute = perMinute;
    }

    public void acquire(Long customerId) {
        Bucket bucket = buckets.computeIfAbsent(customerId, id -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)).build())
                .build());
        if (!bucket.tryConsume(1)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded, retry later");
        }
    }
}
