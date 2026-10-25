package dev.ledger.operations;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/operations")
@PreAuthorize("hasAnyRole('OPERATOR', 'AUDITOR')")
public class OperationsController {

    private final OperationsService operations;

    public OperationsController(OperationsService operations) {
        this.operations = operations;
    }

    @GetMapping("/payments")
    List<OperationsService.PaymentSummary> search(@RequestParam UUID correlationId) {
        return operations.search(correlationId);
    }

    @GetMapping("/health")
    OperationsService.OperationalHealth health() {
        return operations.health();
    }

    @PostMapping("/dead-letters/{eventId}/replay")
    @PreAuthorize("hasRole('OPERATOR')")
    void replay(@PathVariable UUID eventId) {
        operations.replay(eventId);
    }
}
