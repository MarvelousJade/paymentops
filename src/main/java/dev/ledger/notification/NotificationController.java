package dev.ledger.notification;

import dev.ledger.common.ApiException;
import dev.ledger.payment.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments/{paymentId}/deliveries")
public class NotificationController {

    private final NotificationService notifications;
    private final PaymentRepository payments;

    public NotificationController(NotificationService notifications, PaymentRepository payments) {
        this.notifications = notifications;
        this.payments = payments;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_payments:read')")
    List<NotificationService.DeliveryResponse> history(@AuthenticationPrincipal Jwt jwt,
                                                       @PathVariable UUID paymentId) {
        payments.findByIdAndMerchantId(paymentId, Long.valueOf(jwt.getSubject()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "PAYMENT_NOT_FOUND", "Payment not found"));
        return notifications.history(paymentId);
    }
}
