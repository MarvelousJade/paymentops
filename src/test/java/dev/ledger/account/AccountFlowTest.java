package dev.ledger.account;

import dev.ledger.TestcontainersConfiguration;
import dev.ledger.auth.AuthDtos.RegisterRequest;
import dev.ledger.auth.AuthDtos.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AccountFlowTest {

    @LocalServerPort
    int port;

    @Autowired
    AccountStatusChangeRepository statusChanges;

    RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private String registerAndToken() {
        String email = "u" + UUID.randomUUID().toString().substring(0, 8) + "@x.dev";
        TokenResponse tokens = client.post().uri("/auth/register")
                .body(new RegisterRequest(email, "password123", "Account Tester"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(TokenResponse.class)
                .returnResult().getResponseBody();
        return tokens.accessToken();
    }

    private long createAccount(String token) {
        Map<String, Object> body = client.post().uri("/accounts")
                .header("Authorization", "Bearer " + token)
                .body(Map.of("type", "CHEQUING"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        return ((Number) body.get("id")).longValue();
    }

    private void deposit(String token, long accountId, String amount) {
        client.post().uri("/accounts/" + accountId + "/deposits")
                .header("Authorization", "Bearer " + token)
                .body(Map.of("amount", amount))
                .exchange()
                .expectStatus().isCreated();
    }

    @Test
    void createDepositAndListTransactions() {
        String token = registerAndToken();
        long accountId = createAccount(token);

        deposit(token, accountId, "25.00");

        client.get().uri("/accounts/" + accountId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.balance").isEqualTo("25.00")
                .jsonPath("$.status").isEqualTo("OPEN");

        client.get().uri("/accounts/" + accountId + "/transactions")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.content[0].direction").isEqualTo("CREDIT")
                .jsonPath("$.content[0].amount").isEqualTo("25.00");
    }

    @Test
    void paginationSplitsPages() {
        String token = registerAndToken();
        long accountId = createAccount(token);
        deposit(token, accountId, "1.00");
        deposit(token, accountId, "2.00");
        deposit(token, accountId, "3.00");

        client.get().uri("/accounts/" + accountId + "/transactions?page=0&size=2&sort=id&desc=false")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(2)
                .jsonPath("$.content[0].amount").isEqualTo("1.00");

        client.get().uri("/accounts/" + accountId + "/transactions?page=1&size=2&sort=id&desc=false")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.content[0].amount").isEqualTo("3.00");
    }

    @Test
    void unknownSortFieldIs400() {
        String token = registerAndToken();
        long accountId = createAccount(token);

        client.get().uri("/accounts/" + accountId + "/transactions?sort=balanceCents")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void otherCustomersAccountIs404() {
        String owner = registerAndToken();
        long accountId = createAccount(owner);

        String intruder = registerAndToken();
        client.get().uri("/accounts/" + accountId)
                .header("Authorization", "Bearer " + intruder)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void statusChangesAreAuditedAndEnforced() {
        String token = registerAndToken();
        long accountId = createAccount(token);

        client.patch().uri("/accounts/" + accountId + "/status")
                .header("Authorization", "Bearer " + token)
                .body(Map.of("status", "FROZEN"))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("FROZEN");

        // Frozen account refuses deposits.
        client.post().uri("/accounts/" + accountId + "/deposits")
                .header("Authorization", "Bearer " + token)
                .body(Map.of("amount", "1.00"))
                .exchange()
                .expectStatus().isEqualTo(409);

        List<AccountStatusChange> audit = statusChanges.findByAccountIdOrderByChangedAt(accountId);
        assertThat(audit).hasSize(1);
        assertThat(audit.get(0).getOldStatus()).isEqualTo("OPEN");
        assertThat(audit.get(0).getNewStatus()).isEqualTo("FROZEN");
        assertThat(audit.get(0).getChangedBy()).isNotNull();
    }

    @Test
    void closeRequiresZeroBalance() {
        String token = registerAndToken();
        long accountId = createAccount(token);
        deposit(token, accountId, "5.00");

        client.patch().uri("/accounts/" + accountId + "/status")
                .header("Authorization", "Bearer " + token)
                .body(Map.of("status", "CLOSED"))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void malformedAmountIs400() {
        String token = registerAndToken();
        long accountId = createAccount(token);

        client.post().uri("/accounts/" + accountId + "/deposits")
                .header("Authorization", "Bearer " + token)
                .body(Map.of("amount", "10.123"))
                .exchange()
                .expectStatus().isBadRequest();
    }
}
