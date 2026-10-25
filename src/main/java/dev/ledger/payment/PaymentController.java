package dev.ledger.payment;

import dev.ledger.common.CorrelationIds;
import dev.ledger.common.Money;
import dev.ledger.payment.PaymentDtos.CreatePaymentRequest;
import dev.ledger.payment.PaymentDtos.PaymentResponse;
import dev.ledger.payment.PaymentDtos.RefundRequest;
import dev.ledger.payment.PaymentDtos.RefundResponse;
import dev.ledger.payment.PaymentDtos.TimelineResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_payments:write')")
    ResponseEntity<PaymentResponse> create(@AuthenticationPrincipal Jwt jwt,
                                           @RequestHeader("Idempotency-Key") String idempotencyKey,
                                           @Valid @RequestBody CreatePaymentRequest request) {
        PaymentResponse response = payments.create(Long.valueOf(jwt.getSubject()), idempotencyKey,
                request.sourceAccountId(), request.destinationAccountId(), Money.toCents(request.amount()),
                request.currency(), request.paymentToken(), CorrelationIds.current());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize("hasAuthority('SCOPE_payments:read')")
    PaymentResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID paymentId) {
        return payments.get(Long.valueOf(jwt.getSubject()), paymentId);
    }

    @GetMapping("/{paymentId}/timeline")
    @PreAuthorize("hasAuthority('SCOPE_payments:read')")
    List<TimelineResponse> timeline(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID paymentId) {
        return payments.timeline(Long.valueOf(jwt.getSubject()), paymentId);
    }

    @PostMapping("/{paymentId}/capture")
    @PreAuthorize("hasAuthority('SCOPE_payments:write')")
    ResponseEntity<PaymentResponse> capture(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable UUID paymentId,
                                            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        PaymentService.validateKey(idempotencyKey);
        return ResponseEntity.accepted().body(payments.requestCapture(Long.valueOf(jwt.getSubject()), paymentId));
    }

    @PostMapping("/{paymentId}/cancel")
    @PreAuthorize("hasAuthority('SCOPE_payments:write')")
    PaymentResponse cancel(@AuthenticationPrincipal Jwt jwt,
                           @PathVariable UUID paymentId,
                           @RequestHeader("Idempotency-Key") String idempotencyKey) {
        PaymentService.validateKey(idempotencyKey);
        return payments.cancel(Long.valueOf(jwt.getSubject()), paymentId);
    }

    @PostMapping("/{paymentId}/refunds")
    @PreAuthorize("hasAuthority('SCOPE_payments:write')")
    ResponseEntity<RefundResponse> refund(@AuthenticationPrincipal Jwt jwt,
                                          @PathVariable UUID paymentId,
                                          @RequestHeader("Idempotency-Key") String idempotencyKey,
                                          @Valid @RequestBody RefundRequest request) {
        return ResponseEntity.accepted().body(payments.requestRefund(Long.valueOf(jwt.getSubject()),
                paymentId, idempotencyKey, Money.toCents(request.amount())));
    }
}
