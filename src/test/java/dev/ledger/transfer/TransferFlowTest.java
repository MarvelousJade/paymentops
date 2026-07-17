package dev.ledger.transfer;

import dev.ledger.TestcontainersConfiguration;
import dev.ledger.auth.AuthDtos.RegisterRequest;
import dev.ledger.auth.AuthDtos.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.Map;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class TransferFlowTest {

    @LocalServerPort
    int port;

    RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private String registerAndToken() {
        String email = "u" + UUID.randomUUID().toString().substring(0, 8) + "@x.dev";
        return client.post().uri("/auth/register")
                .body(new RegisterRequest(email, "password123", "Transfer Tester"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(TokenResponse.class)
                .returnResult().getResponseBody()
                .accessToken();
    }

    private long createFundedAccount(String token, String amount) {
        Map<String, Object> body = client.post().uri("/accounts")
                .header("Authorization", "Bearer " + token)
                .body(Map.of("type", "CHEQUING"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        long id = ((Number) body.get("id")).longValue();
        if (amount != null) {
            client.post().uri("/accounts/" + id + "/deposits")
                    .header("Authorization", "Bearer " + token)
                    .body(Map.of("amount", amount))
                    .exchange()
                    .expectStatus().isCreated();
        }
        return id;
    }

    private RestTestClient.ResponseSpec transfer(String token, String key, long from, long to, String amount) {
        return client.post().uri("/transfers")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key)
                .body(Map.of("fromAccountId", from, "toAccountId", to, "amount", amount))
                .exchange();
    }

    @Test
    void transferMovesMoneyAndReplayReturnsOriginal() {
        String token = registerAndToken();
        long from = createFundedAccount(token, "100.00");
        long to = createFundedAccount(token, "100.00");
        String key = UUID.randomUUID().toString();

        Map<String, Object> first = transfer(token, key, from, to, "30.00")
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        org.assertj.core.api.Assertions.assertThat(first.get("status")).isEqualTo("COMPLETED");

        // Replay with the same key: same transfer id, no double spend.
        Map<String, Object> replay = transfer(token, key, from, to, "30.00")
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        org.assertj.core.api.Assertions.assertThat(replay.get("id")).isEqualTo(first.get("id"));

        client.get().uri("/accounts/" + from)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectBody().jsonPath("$.balance").isEqualTo("70.00");
        client.get().uri("/accounts/" + to)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectBody().jsonPath("$.balance").isEqualTo("130.00");

        client.get().uri("/transfers/" + first.get("id"))
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("COMPLETED");
    }

    @Test
    void insufficientFundsFailsAndReplayReturnsSameFailure() {
        String token = registerAndToken();
        long from = createFundedAccount(token, "10.00");
        long to = createFundedAccount(token, null);
        String key = UUID.randomUUID().toString();

        transfer(token, key, from, to, "50.00")
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("FAILED")
                .jsonPath("$.failureReason").isEqualTo("Insufficient funds");

        transfer(token, key, from, to, "50.00")
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.status").isEqualTo("FAILED");

        client.get().uri("/accounts/" + from)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectBody().jsonPath("$.balance").isEqualTo("10.00");
    }

    @Test
    void transferToFrozenAccountFails() {
        String token = registerAndToken();
        long from = createFundedAccount(token, "50.00");
        long to = createFundedAccount(token, null);
        client.patch().uri("/accounts/" + to + "/status")
                .header("Authorization", "Bearer " + token)
                .body(Map.of("status", "FROZEN"))
                .exchange()
                .expectStatus().isOk();

        transfer(token, UUID.randomUUID().toString(), from, to, "5.00")
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("FAILED")
                .jsonPath("$.failureReason").isEqualTo("Destination account is FROZEN");
    }

    @Test
    void sameAccountTransferIs400() {
        String token = registerAndToken();
        long account = createFundedAccount(token, "50.00");

        transfer(token, UUID.randomUUID().toString(), account, account, "5.00")
                .expectStatus().isBadRequest();
    }

    @Test
    void missingIdempotencyKeyIs400() {
        String token = registerAndToken();
        long from = createFundedAccount(token, "50.00");
        long to = createFundedAccount(token, null);

        client.post().uri("/transfers")
                .header("Authorization", "Bearer " + token)
                .body(Map.of("fromAccountId", from, "toAccountId", to, "amount", "5.00"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void foreignSourceAccountIs404() {
        String owner = registerAndToken();
        long foreign = createFundedAccount(owner, "50.00");

        String intruder = registerAndToken();
        long to = createFundedAccount(intruder, null);

        transfer(intruder, UUID.randomUUID().toString(), foreign, to, "5.00")
                .expectStatus().isNotFound();
    }

    @Test
    void crossCustomerPayeeWorks() {
        String alice = registerAndToken();
        long from = createFundedAccount(alice, "50.00");

        String bob = registerAndToken();
        long bobAccount = createFundedAccount(bob, null);

        transfer(alice, UUID.randomUUID().toString(), from, bobAccount, "20.00")
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.status").isEqualTo("COMPLETED");

        client.get().uri("/accounts/" + bobAccount)
                .header("Authorization", "Bearer " + bob)
                .exchange()
                .expectBody().jsonPath("$.balance").isEqualTo("20.00");
    }
}
