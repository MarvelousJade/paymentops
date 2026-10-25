package dev.ledger.reconciliation;

import dev.ledger.common.Money;
import dev.ledger.reconciliation.ReconciliationDtos.ActionRequest;
import dev.ledger.reconciliation.ReconciliationDtos.ExceptionResponse;
import dev.ledger.reconciliation.ReconciliationDtos.RunResponse;
import dev.ledger.settlement.SettlementDtos.ImportSettlementRequest;
import dev.ledger.settlement.SettlementDtos.SettlementResponse;
import dev.ledger.settlement.SettlementSimulatorService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/operations/reconciliation")
@PreAuthorize("hasAnyRole('OPERATOR', 'AUDITOR')")
public class ReconciliationController {

    private final ReconciliationService reconciliation;
    private final SettlementSimulatorService settlement;

    public ReconciliationController(ReconciliationService reconciliation,
                                    SettlementSimulatorService settlement) {
        this.reconciliation = reconciliation;
        this.settlement = settlement;
    }

    @GetMapping("/exceptions")
    List<ExceptionResponse> list(@RequestParam(required = false) ReconciliationException.Status status) {
        return reconciliation.list(status);
    }

    @PostMapping("/run")
    @PreAuthorize("hasRole('OPERATOR')")
    RunResponse run() {
        return reconciliation.reconcileAll();
    }

    @PostMapping("/exceptions/{id}/acknowledge")
    @PreAuthorize("hasRole('OPERATOR')")
    ExceptionResponse acknowledge(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                  @Valid @RequestBody ActionRequest request) {
        return reconciliation.acknowledge(id, jwt.getSubject(), request.notes());
    }

    @PostMapping("/exceptions/{id}/resolve")
    @PreAuthorize("hasRole('OPERATOR')")
    ExceptionResponse resolve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                              @Valid @RequestBody ActionRequest request) {
        return reconciliation.resolve(id, jwt.getSubject(), request.notes());
    }

    @PostMapping("/settlements")
    @PreAuthorize("hasRole('OPERATOR')")
    SettlementResponse importSettlement(@Valid @RequestBody ImportSettlementRequest request) {
        return SettlementResponse.from(settlement.importRecord(request.paymentId(), request.externalReference(),
                Money.toCents(request.amount()), request.currency(), request.status()));
    }

    @PostMapping("/settlements/{paymentId}/complete-delayed")
    @PreAuthorize("hasRole('OPERATOR')")
    void completeDelayed(@PathVariable UUID paymentId) {
        settlement.completeDelayed(paymentId);
    }
}
