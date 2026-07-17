package dev.ledger.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ledger.jwt")
public record JwtProps(String secret, Duration accessTtl, Duration refreshTtl) {
}
