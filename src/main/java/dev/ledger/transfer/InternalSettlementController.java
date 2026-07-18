package dev.ledger.transfer;

import dev.ledger.common.ApiException;
import dev.ledger.transfer.TransferDtos.TransferResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.codec.Utf8;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;

/**
 * Service-to-service surface for the settlement Lambda. Authenticated by a shared
 * static token, not JWT: the caller is infrastructure, not a customer.
 */
@RestController
@RequestMapping("/internal/transfers")
public class InternalSettlementController {

    private final TransferService transferService;
    private final String serviceToken;

    public InternalSettlementController(TransferService transferService,
                                        @Value("${ledger.internal.token}") String serviceToken) {
        this.transferService = transferService;
        this.serviceToken = serviceToken;
    }

    record FailRequest(@NotBlank String reason) {
    }

    @PostMapping("/{id}/settle")
    TransferResponse settle(@RequestHeader(value = "X-Service-Token", required = false) String token,
                            @PathVariable Long id) {
        requireServiceToken(token);
        return transferService.settleInternal(id);
    }

    @PostMapping("/{id}/fail")
    TransferResponse fail(@RequestHeader(value = "X-Service-Token", required = false) String token,
                          @PathVariable Long id, @Valid @RequestBody FailRequest request) {
        requireServiceToken(token);
        return transferService.failInternal(id, request.reason());
    }

    private void requireServiceToken(String token) {
        // Blank configured token = endpoints disabled (sync-only deployments).
        if (serviceToken.isBlank() || token == null
                || !MessageDigest.isEqual(Utf8.encode(serviceToken), Utf8.encode(token))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid service token");
        }
    }
}
