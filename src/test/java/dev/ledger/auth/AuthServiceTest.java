package dev.ledger.auth;

import dev.ledger.common.ApiException;
import dev.ledger.common.JwtProps;
import dev.ledger.customer.Customer;
import dev.ledger.customer.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private CustomerRepository customers;
    private RefreshTokenRepository refreshTokens;
    private PasswordEncoder passwordEncoder;
    private AuthService service;

    @BeforeEach
    void setUp() {
        customers = mock(CustomerRepository.class);
        refreshTokens = mock(RefreshTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        JwtProps props = new JwtProps("unused", Duration.ofMinutes(15), Duration.ofDays(7));
        service = new AuthService(customers, refreshTokens, passwordEncoder, mock(JwtEncoder.class), props);
    }

    @Test
    void loginRejectsUnknownEmail() {
        when(customers.findByEmail("no@x.dev")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("no@x.dev", "pw"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void loginRejectsWrongPassword() {
        when(customers.findByEmail("a@x.dev")).thenReturn(Optional.of(new Customer("a@x.dev", "hash", "A")));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login("a@x.dev", "wrong"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void reusedRefreshTokenRevokesWholeFamily() {
        RefreshToken revoked = new RefreshToken(7L, AuthService.sha256("stolen"), Instant.now().plus(Duration.ofDays(1)));
        revoked.revoke();
        when(refreshTokens.findByTokenHash(AuthService.sha256("stolen"))).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.refresh("stolen")).isInstanceOf(ApiException.class);
        verify(refreshTokens).revokeAllForCustomer(eq(7L), any(Instant.class));
    }

    @Test
    void expiredRefreshTokenRejected() {
        RefreshToken expired = new RefreshToken(7L, AuthService.sha256("old"), Instant.now().minusSeconds(1));
        when(refreshTokens.findByTokenHash(AuthService.sha256("old"))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.refresh("old")).isInstanceOf(ApiException.class);
    }
}
