package dev.ledger.transfer;

import dev.ledger.common.Money;
import dev.ledger.transfer.TransferDtos.TransferRequest;
import dev.ledger.transfer.TransferDtos.TransferResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TransferResponse create(@AuthenticationPrincipal Jwt jwt,
                            @RequestHeader("Idempotency-Key") String idempotencyKey,
                            @Valid @RequestBody TransferRequest request) {
        return transferService.create(Long.valueOf(jwt.getSubject()), idempotencyKey,
                request.fromAccountId(), request.toAccountId(), Money.toCents(request.amount()));
    }

    @GetMapping("/{id}")
    TransferResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return transferService.get(Long.valueOf(jwt.getSubject()), id);
    }
}
