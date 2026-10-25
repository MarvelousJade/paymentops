package dev.ledger.payment;

import dev.ledger.TestcontainersConfiguration;
import dev.ledger.auth.AuthDtos.RegisterRequest;
import dev.ledger.auth.AuthDtos.TokenResponse;
import dev.ledger.event.EventEnvelope;
import dev.ledger.event.EventRouter;
import dev.ledger.event.OutboxDispatcher;
import dev.ledger.event.OutboxEvent;
import dev.ledger.event.OutboxEventRepository;
import dev.ledger.reconciliation.ReconciliationException;
import dev.ledger.reconciliation.ReconciliationExceptionRepository;
import dev.ledger.reconciliation.ReconciliationService;
import dev.ledger.settlement.SettlementRecord;
import dev.ledger.settlement.SettlementSimulatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "paymentops.outbox.poll-delay-ms=3600000",
        "paymentops.webhook.poll-delay-ms=3600000"})
@Import(TestcontainersConfiguration.class)
class PaymentOpsFlowTest {

    @LocalServerPort
    int port;

    @Autowired
    OutboxDispatcher dispatcher;
    @Autowired
    OutboxEventRepository outbox;
    @Autowired
    EventRouter router;
    @Autowired
    PaymentRepository payments;
    @Autowired
    PaymentLedgerPostingRepository postings;
    @Autowired
    ReconciliationService reconciliation;
    @Autowired
    ReconciliationExceptionRepository exceptions;
    @Autowired
    SettlementSimulatorService settlement;

    @Autowired
    dev.ledger.settlement.SettlementRecordRepository settlementRecords;

    RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void authorizedPaymentPostsOnceSettlesReconcilesAndRefunds() {
        String merchant = register();
        long source = account(merchant, "100.00");
        String payee = register();
        long destination = account(payee, null);
        String idempotencyKey = UUID.randomUUID().toString();

        Map<String, Object> first = createPayment(merchant, idempotencyKey, source, destination, "30.00")
                .expectStatus().isAccepted()
                .expectBody(Map.class).returnResult().getResponseBody();
        UUID paymentId = UUID.fromString((String) first.get("id"));
        String correlationId = (String) first.get("correlationId");

        Map<String, Object> replay = createPayment(merchant, idempotencyKey, source, destination, "30.00")
                .expectStatus().isAccepted()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(replay.get("id")).isEqualTo(first.get("id"));

        drain(2);
        client.get().uri("/api/v1/payments/" + paymentId)
                .header("Authorization", bearer(merchant))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("AUTHORIZED");

        client.post().uri("/api/v1/payments/" + paymentId + "/capture")
                .header("Authorization", bearer(merchant))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .exchange().expectStatus().isAccepted();

        drain(5);
        client.get().uri("/api/v1/payments/" + paymentId)
                .header("Authorization", bearer(merchant))
                .exchange().expectBody()
                .jsonPath("$.status").isEqualTo("SETTLED")
                .jsonPath("$.amount").isEqualTo("30.00");
        assertThat(postings.findByPaymentIdAndOperationKey(paymentId, "CAPTURE")).isPresent();
        assertThat(postings.countByPaymentId(paymentId)).isEqualTo(1);
        assertThat(exceptions.findByPaymentId(paymentId)).isEmpty();

        client.get().uri("/accounts/" + source)
                .header("Authorization", bearer(merchant))
                .exchange().expectBody().jsonPath("$.balance").isEqualTo("70.00");
        client.get().uri("/accounts/" + destination)
                .header("Authorization", bearer(payee))
                .exchange().expectBody().jsonPath("$.balance").isEqualTo("30.00");

        OutboxEvent capture = outbox.findAll().stream()
                .filter(event -> event.getAggregateId().equals(paymentId.toString()))
                .filter(event -> event.getEventType().equals(PaymentService.CAPTURE_REQUESTED))
                .findFirst().orElseThrow();
        router.route(EventEnvelope.from(capture, new com.fasterxml.jackson.databind.ObjectMapper()
                .findAndRegisterModules()));
        assertThat(postings.countByPaymentId(paymentId)).isEqualTo(1);

        client.post().uri("/api/v1/payments/" + paymentId + "/refunds")
                .header("Authorization", bearer(merchant))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .body(Map.of("amount", "10.00"))
                .exchange().expectStatus().isAccepted();
        drain(3);
        client.get().uri("/api/v1/payments/" + paymentId)
                .header("Authorization", bearer(merchant))
                .exchange().expectBody()
                .jsonPath("$.status").isEqualTo("PARTIALLY_REFUNDED")
                .jsonPath("$.refundedAmount").isEqualTo("10.00");
        assertThat(postings.countByPaymentId(paymentId)).isEqualTo(2);

        client.get().uri("/api/v1/payments/" + paymentId + "/timeline")
                .header("Authorization", bearer(merchant))
                .exchange().expectBody()
                .jsonPath("$[0].eventType").isEqualTo("payment.requested.v1");
        assertThat(correlationId).isNotBlank();
    }

    @Test
    void refundBeforeSettlementDoesNotDiscardSettlementAndCaptureRetryRemainsSafe() {
        String merchant = register();
        long source = account(merchant, "100.00");
        long destination = account(register(), null);
        UUID paymentId = UUID.fromString((String) createPayment(merchant, UUID.randomUUID().toString(),
                source, destination, "30.00").expectBody(Map.class)
                .returnResult().getResponseBody().get("id"));
        drain(2);
        client.post().uri("/api/v1/payments/" + paymentId + "/capture")
                .header("Authorization", bearer(merchant))
                .header("Idempotency-Key", "capture-retry")
                .exchange().expectStatus().isAccepted();
        dispatcher.dispatch(); // CAPTURED, before the settlement consumer runs
        client.post().uri("/api/v1/payments/" + paymentId + "/refunds")
                .header("Authorization", bearer(merchant))
                .header("Idempotency-Key", "early-refund")
                .body(Map.of("amount", "10.00"))
                .exchange().expectStatus().isAccepted();
        // Independent Kafka topics may deliver refund before capture-posted.
        OutboxEvent refund = outbox.findAll().stream()
                .filter(e -> e.getAggregateId().equals(paymentId.toString()))
                .filter(e -> e.getEventType().equals(PaymentService.REFUND_REQUESTED))
                .findFirst().orElseThrow();
        router.route(EventEnvelope.from(refund, new com.fasterxml.jackson.databind.ObjectMapper()
                .findAndRegisterModules()));
        assertThat(payments.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(Payment.Status.PARTIALLY_REFUNDED);
        drain(5);
        assertThat(settlementRecords.findByPaymentIdOrderByReceivedAt(paymentId))
                .hasSize(1).allMatch(r -> r.getStatus() == SettlementRecord.Status.SETTLED);
        assertThat(payments.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(Payment.Status.PARTIALLY_REFUNDED);
        settlement.importRecord(paymentId, "wrong-amount-" + paymentId, 2_900, "CAD",
                SettlementRecord.Status.SETTLED);
        reconciliation.reconcileAll();
        assertThat(exceptions.findByPaymentIdAndClassification(paymentId,
                ReconciliationException.Classification.AMOUNT_MISMATCH)).isPresent();
        client.post().uri("/api/v1/payments/" + paymentId + "/capture")
                .header("Authorization", bearer(merchant))
                .header("Idempotency-Key", "capture-retry")
                .exchange().expectStatus().isAccepted();
        assertThat(postings.countByPaymentId(paymentId)).isEqualTo(2);
    }

    @Test
    void sameCreateKeyRejectsChangedAmountWithoutCreatingFinancialEffects() {
        String merchant = register();
        long source = account(merchant, "100.00");
        long destination = account(register(), null);
        String key = UUID.randomUUID().toString();
        Map first = createPayment(merchant, key, source, destination, "30.00")
                .expectStatus().isAccepted().expectBody(Map.class).returnResult().getResponseBody();
        UUID id = UUID.fromString((String) first.get("id"));
        createPayment(merchant, key, source, destination, "31.00")
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(payments.findById(id).orElseThrow().getAmountCents()).isEqualTo(3_000);
        assertThat(postings.countByPaymentId(id)).isZero();
        client.get().uri("/accounts/" + source).header("Authorization", bearer(merchant))
                .exchange().expectBody().jsonPath("$.balance").isEqualTo("100.00");
    }

    @Test
    void deterministicRiskRejectsLimitAndInvalidTransitionIsSafe() {
        String merchant = register();
        long source = account(merchant, "1.00");
        long destination = account(register(), null);

        Map<String, Object> created = createPayment(merchant, UUID.randomUUID().toString(),
                source, destination, "10000.01")
                .expectStatus().isAccepted()
                .expectBody(Map.class).returnResult().getResponseBody();
        UUID paymentId = UUID.fromString((String) created.get("id"));
        drain(3);

        client.get().uri("/api/v1/payments/" + paymentId)
                .header("Authorization", bearer(merchant))
                .exchange().expectBody().jsonPath("$.status").isEqualTo("REJECTED");
        client.post().uri("/api/v1/payments/" + paymentId + "/capture")
                .header("Authorization", bearer(merchant))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_PAYMENT_TRANSITION");
        assertThat(postings.countByPaymentId(paymentId)).isZero();
    }

    @Test
    void reconciliationClassifiesMissingDuplicateAndAmountMismatchWithoutDeletingEvidence() {
        String merchant = register();
        long source = account(merchant, "100.00");
        long destination = account(register(), null);
        UUID paymentId = UUID.fromString((String) createPayment(merchant, UUID.randomUUID().toString(),
                source, destination, "25.00").expectBody(Map.class)
                .returnResult().getResponseBody().get("id"));
        drain(2);
        client.post().uri("/api/v1/payments/" + paymentId + "/capture")
                .header("Authorization", bearer(merchant))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .exchange().expectStatus().isAccepted();

        dispatcher.dispatch(); // capture posting; settlement event remains in the outbox
        reconciliation.reconcilePayment(paymentId);
        assertThat(exceptions.findByPaymentIdAndClassification(paymentId,
                ReconciliationException.Classification.MISSING_SETTLEMENT)).isPresent();

        drain(4);
        ReconciliationException missing = exceptions.findByPaymentIdAndClassification(paymentId,
                ReconciliationException.Classification.MISSING_SETTLEMENT).orElseThrow();
        assertThat(missing.getStatus()).isEqualTo(ReconciliationException.Status.RESOLVED);

        settlement.importRecord(paymentId, "rail-extra-" + paymentId, 2_400, "CAD",
                SettlementRecord.Status.SETTLED);
        reconciliation.reconcilePayment(paymentId);
        assertThat(exceptions.findByPaymentIdAndClassification(paymentId,
                ReconciliationException.Classification.DUPLICATE_SETTLEMENT)).isPresent();
        assertThat(exceptions.findByPaymentIdAndClassification(paymentId,
                ReconciliationException.Classification.AMOUNT_MISMATCH)).isPresent();
    }

    @Test
    void authenticationScopesAndRequiredHeadersProtectPaymentOperations() {
        String token = register();
        long source = account(token, "10.00");
        long destination = account(register(), null);

        client.post().uri("/api/v1/payments")
                .body(Map.of("sourceAccountId", source, "destinationAccountId", destination,
                        "amount", "1.00", "currency", "CAD", "paymentToken", "tok_testtoken1"))
                .exchange().expectStatus().isUnauthorized();

        client.post().uri("/api/v1/payments")
                .header("Authorization", bearer(token))
                .body(Map.of("sourceAccountId", source, "destinationAccountId", destination,
                        "amount", "1.00", "currency", "CAD", "paymentToken", "tok_testtoken1"))
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("MISSING_HEADER");

        client.get().uri("/api/v1/operations/health")
                .header("Authorization", bearer(token))
                .exchange().expectStatus().isForbidden();
    }

    private RestTestClient.ResponseSpec createPayment(String token, String key, long source,
                                                      long destination, String amount) {
        return client.post().uri("/api/v1/payments")
                .header("Authorization", bearer(token))
                .header("Idempotency-Key", key)
                .body(Map.of("sourceAccountId", source, "destinationAccountId", destination,
                        "amount", amount, "currency", "CAD", "paymentToken", "tok_testtoken1"))
                .exchange();
    }

    private String register() {
        String email = "p" + UUID.randomUUID().toString().substring(0, 10) + "@test.dev";
        return client.post().uri("/auth/register")
                .body(new RegisterRequest(email, "password123", "Payment Tester"))
                .exchange().expectStatus().isCreated()
                .expectBody(TokenResponse.class).returnResult().getResponseBody().accessToken();
    }

    private long account(String token, String amount) {
        Map<String, Object> body = client.post().uri("/accounts")
                .header("Authorization", bearer(token))
                .body(Map.of("type", "CHEQUING"))
                .exchange().expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody();
        long id = ((Number) body.get("id")).longValue();
        if (amount != null) {
            client.post().uri("/accounts/" + id + "/deposits")
                    .header("Authorization", bearer(token))
                    .body(Map.of("amount", amount))
                    .exchange().expectStatus().isCreated();
        }
        return id;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private void drain(int passes) {
        for (int i = 0; i < passes; i++) {
            dispatcher.dispatch();
        }
    }
}
