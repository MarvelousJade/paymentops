package dev.ledger.transfer;

import dev.ledger.TestcontainersConfiguration;
import dev.ledger.auth.AuthDtos.RegisterRequest;
import dev.ledger.auth.AuthDtos.TokenResponse;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The async seam, minus Lambda: POST /transfers publishes to (LocalStack) SQS and returns 202;
 * this test plays the Lambda's part by reading the message and calling the internal endpoints.
 * The Lambda's own logic is covered by its unit tests in settlement-lambda.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "ledger.settlement.mode=async",
        "ledger.settlement.queue=ledger-settlement-test",
        "ledger.internal.token=test-service-token"})
@Import(TestcontainersConfiguration.class)
@Testcontainers
class AsyncSettlementFlowTest {

    @Container
    static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3"))
            .withServices(LocalStackContainer.Service.SQS);

    @DynamicPropertySource
    static void aws(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.aws.endpoint", () -> LOCALSTACK.getEndpoint().toString());
        registry.add("spring.cloud.aws.region.static", LOCALSTACK::getRegion);
        registry.add("spring.cloud.aws.credentials.access-key", LOCALSTACK::getAccessKey);
        registry.add("spring.cloud.aws.credentials.secret-key", LOCALSTACK::getSecretKey);
    }

    @LocalServerPort
    int port;

    @Autowired
    SqsTemplate sqsTemplate;

    RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        try (SqsClient sqs = SqsClient.builder()
                .endpointOverride(LOCALSTACK.getEndpoint())
                .region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                .build()) {
            sqs.createQueue(b -> b.queueName("ledger-settlement-test"));
        }
    }

    private String registerAndToken() {
        return client.post().uri("/auth/register")
                .body(new RegisterRequest("a" + UUID.randomUUID().toString().substring(0, 8) + "@x.dev",
                        "password123", "Async Tester"))
                .exchange().expectStatus().isCreated()
                .expectBody(TokenResponse.class).returnResult().getResponseBody().accessToken();
    }

    private long fundedAccount(String token, String amount) {
        Map<String, Object> body = client.post().uri("/accounts")
                .header("Authorization", "Bearer " + token)
                .body(Map.of("type", "CHEQUING"))
                .exchange().expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody();
        long id = ((Number) body.get("id")).longValue();
        if (amount != null) {
            client.post().uri("/accounts/" + id + "/deposits")
                    .header("Authorization", "Bearer " + token)
                    .body(Map.of("amount", amount))
                    .exchange().expectStatus().isCreated();
        }
        return id;
    }

    @Test
    void asyncTransferPublishesThenInternalCallbackSettles() {
        String token = registerAndToken();
        long from = fundedAccount(token, "100.00");
        long to = fundedAccount(token, null);

        // 1. Client sees 202 + PENDING.
        Map<String, Object> created = client.post().uri("/transfers")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .body(Map.of("fromAccountId", from, "toAccountId", to, "amount", "40.00"))
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(Map.class).returnResult().getResponseBody();
        long transferId = ((Number) created.get("id")).longValue();
        assertThat(created.get("status")).isEqualTo("PENDING");

        // 2. The message really landed on the queue with the right payload.
        // Both tests publish to the same queue; drain until ours shows up.
        TransferDtos.SettlementMessage payload = null;
        for (int i = 0; i < 5 && payload == null; i++) {
            var message = sqsTemplate.receive("ledger-settlement-test", TransferDtos.SettlementMessage.class);
            if (message.isPresent() && message.get().getPayload().transferId() == transferId) {
                payload = message.get().getPayload();
            }
        }
        assertThat(payload).as("settlement message for transfer %d", transferId).isNotNull();
        assertThat(payload.amountCents()).isEqualTo(4_000);

        // 3. Playing the Lambda: settle via the internal endpoint.
        client.post().uri("/internal/transfers/" + transferId + "/settle")
                .header("X-Service-Token", "test-service-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("COMPLETED");

        // 4. Redelivery: second settle is a no-op, money moved once.
        client.post().uri("/internal/transfers/" + transferId + "/settle")
                .header("X-Service-Token", "test-service-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("COMPLETED");

        client.get().uri("/accounts/" + from)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectBody().jsonPath("$.balance").isEqualTo("60.00");

        // 5. Client polls to the final state.
        client.get().uri("/transfers/" + transferId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectBody().jsonPath("$.status").isEqualTo("COMPLETED");
    }

    @Test
    void failCallbackPersistsReasonAndWrongTokenRejected() {
        String token = registerAndToken();
        long from = fundedAccount(token, "50.00");
        long to = fundedAccount(token, null);

        Map<String, Object> created = client.post().uri("/transfers")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .body(Map.of("fromAccountId", from, "toAccountId", to, "amount", "10.00"))
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(Map.class).returnResult().getResponseBody();
        long transferId = ((Number) created.get("id")).longValue();

        // Wrong/missing token never reaches the ledger.
        client.post().uri("/internal/transfers/" + transferId + "/settle")
                .header("X-Service-Token", "wrong")
                .exchange().expectStatus().isUnauthorized();
        client.post().uri("/internal/transfers/" + transferId + "/settle")
                .exchange().expectStatus().isUnauthorized();

        // Lambda rejected it (e.g. velocity): FAILED with reason, funds untouched.
        client.post().uri("/internal/transfers/" + transferId + "/fail")
                .header("X-Service-Token", "test-service-token")
                .body(Map.of("reason", "Daily transfer limit exceeded"))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("FAILED");

        client.get().uri("/transfers/" + transferId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectBody()
                .jsonPath("$.status").isEqualTo("FAILED")
                .jsonPath("$.failureReason").isEqualTo("Daily transfer limit exceeded");

        client.get().uri("/accounts/" + from)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectBody().jsonPath("$.balance").isEqualTo("50.00");
    }
}
