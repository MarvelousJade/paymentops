package dev.ledger.event;

import dev.ledger.notification.NotificationService;
import dev.ledger.payment.LedgerPostingService;
import dev.ledger.payment.PaymentService;
import dev.ledger.reconciliation.ReconciliationService;
import dev.ledger.risk.RiskRulesService;
import dev.ledger.settlement.SettlementSimulatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class EventRouter {

    private static final Logger log = LoggerFactory.getLogger(EventRouter.class);
    private static final Set<String> NOTIFICATION_EVENTS = Set.of(
            RiskRulesService.APPROVED,
            RiskRulesService.REJECTED,
            PaymentService.CANCELLED,
            LedgerPostingService.FAILED,
            LedgerPostingService.REFUNDED,
            LedgerPostingService.REFUND_FAILED,
            SettlementSimulatorService.SETTLED,
            SettlementSimulatorService.SETTLEMENT_FAILED);

    private final RiskRulesService risk;
    private final LedgerPostingService ledger;
    private final SettlementSimulatorService settlement;
    private final ReconciliationService reconciliation;
    private final NotificationService notifications;

    public EventRouter(RiskRulesService risk, LedgerPostingService ledger,
                       SettlementSimulatorService settlement, ReconciliationService reconciliation,
                       NotificationService notifications) {
        this.risk = risk;
        this.ledger = ledger;
        this.settlement = settlement;
        this.reconciliation = reconciliation;
        this.notifications = notifications;
    }

    public void route(EventEnvelope event) {
        switch (event.eventType()) {
            case PaymentService.REQUESTED -> risk.process(event);
            case PaymentService.CAPTURE_REQUESTED -> ledger.processCapture(event);
            case PaymentService.REFUND_REQUESTED -> ledger.processRefund(event);
            case LedgerPostingService.CAPTURE_POSTED -> settlement.process(event);
            case SettlementSimulatorService.SETTLED -> reconciliation.processSettled(event);
            default -> log.debug("No state processor for eventType={}", event.eventType());
        }
        if (NOTIFICATION_EVENTS.contains(event.eventType())) {
            notifications.queue(event);
        }
    }
}
