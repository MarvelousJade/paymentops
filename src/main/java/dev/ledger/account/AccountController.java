package dev.ledger.account;

import dev.ledger.account.AccountDtos.AccountResponse;
import dev.ledger.account.AccountDtos.CreateAccountRequest;
import dev.ledger.account.AccountDtos.DepositRequest;
import dev.ledger.account.AccountDtos.LedgerEntryResponse;
import dev.ledger.account.AccountDtos.StatusRequest;
import dev.ledger.common.Money;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    private static Long customerId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AccountResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateAccountRequest request) {
        return accountService.create(customerId(jwt), request.type());
    }

    @GetMapping
    List<AccountResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return accountService.list(customerId(jwt));
    }

    @GetMapping("/{id}")
    AccountResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return accountService.get(customerId(jwt), id);
    }

    @PostMapping("/{id}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    AccountResponse deposit(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                            @Valid @RequestBody DepositRequest request) {
        return accountService.deposit(customerId(jwt), id, Money.toCents(request.amount()));
    }

    @PatchMapping("/{id}/status")
    AccountResponse changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                 @Valid @RequestBody StatusRequest request) {
        return accountService.changeStatus(customerId(jwt), id, request.status());
    }

    @GetMapping("/{id}/transactions")
    Page<LedgerEntryResponse> transactions(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                           @RequestParam(required = false) Instant from,
                                           @RequestParam(required = false) Instant to,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(defaultValue = "createdAt") String sort,
                                           @RequestParam(defaultValue = "true") boolean desc) {
        return accountService.transactions(customerId(jwt), id, from, to, page, size, sort, desc);
    }
}
