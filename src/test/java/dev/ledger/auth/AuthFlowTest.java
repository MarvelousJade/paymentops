package dev.ledger.auth;

import dev.ledger.TestcontainersConfiguration;
import dev.ledger.auth.AuthDtos.LoginRequest;
import dev.ledger.auth.AuthDtos.RefreshRequest;
import dev.ledger.auth.AuthDtos.RegisterRequest;
import dev.ledger.auth.AuthDtos.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AuthFlowTest {

    @LocalServerPort
    int port;

    @Autowired
    RefreshTokenRepository refreshTokens;

    RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private static String uniqueEmail() {
        return "u" + UUID.randomUUID().toString().substring(0, 8) + "@x.dev";
    }

    private TokenResponse register(String email) {
        return client.post().uri("/auth/register")
                .body(new RegisterRequest(email, "password123", "Test User"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(TokenResponse.class)
                .returnResult().getResponseBody();
    }

    @Test
    void registerLoginMeHappyPath() {
        String email = uniqueEmail();
        register(email);

        TokenResponse login = client.post().uri("/auth/login")
                .body(new LoginRequest(email, "password123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TokenResponse.class)
                .returnResult().getResponseBody();

        client.get().uri("/auth/me")
                .header("Authorization", "Bearer " + login.accessToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.email").isEqualTo(email);
    }

    @Test
    void loginWithBadPasswordIs401() {
        String email = uniqueEmail();
        register(email);

        client.post().uri("/auth/login")
                .body(new LoginRequest(email, "wrong-password"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void meWithoutTokenIs401() {
        client.get().uri("/auth/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void validationFailureIs400WithFieldErrors() {
        client.post().uri("/auth/register")
                .body(new RegisterRequest("not-an-email", "short", ""))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.email").exists()
                .jsonPath("$.errors.password").exists();
    }

    @Test
    void refreshRotatesAndDetectsReuse() {
        TokenResponse initial = register(uniqueEmail());

        TokenResponse rotated = client.post().uri("/auth/refresh")
                .body(new RefreshRequest(initial.refreshToken()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TokenResponse.class)
                .returnResult().getResponseBody();
        assertThat(rotated.refreshToken()).isNotEqualTo(initial.refreshToken());

        // Reusing the rotated-away token must fail and revoke the whole family.
        client.post().uri("/auth/refresh")
                .body(new RefreshRequest(initial.refreshToken()))
                .exchange()
                .expectStatus().isUnauthorized();

        client.post().uri("/auth/refresh")
                .body(new RefreshRequest(rotated.refreshToken()))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void expiredRefreshTokenIs401() {
        TokenResponse initial = register(uniqueEmail());

        RefreshToken stored = refreshTokens.findByTokenHash(AuthService.sha256(initial.refreshToken())).orElseThrow();
        String expiredRaw = "expired-" + UUID.randomUUID();
        refreshTokens.save(new RefreshToken(stored.getCustomerId(), AuthService.sha256(expiredRaw),
                Instant.now().minusSeconds(60)));

        client.post().uri("/auth/refresh")
                .body(new RefreshRequest(expiredRaw))
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
