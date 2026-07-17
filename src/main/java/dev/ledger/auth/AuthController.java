package dev.ledger.auth;

import dev.ledger.auth.AuthDtos.LoginRequest;
import dev.ledger.auth.AuthDtos.MeResponse;
import dev.ledger.auth.AuthDtos.RefreshRequest;
import dev.ledger.auth.AuthDtos.RegisterRequest;
import dev.ledger.auth.AuthDtos.TokenResponse;
import dev.ledger.common.ApiException;
import dev.ledger.customer.Customer;
import dev.ledger.customer.CustomerRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final CustomerRepository customers;

    public AuthController(AuthService authService, CustomerRepository customers) {
        this.authService = authService;
        this.customers = customers;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request.email(), request.password(), request.fullName());
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/refresh")
    TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @GetMapping("/me")
    MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        Customer customer = customers.findById(Long.valueOf(jwt.getSubject()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unknown customer"));
        return new MeResponse(customer.getId(), customer.getEmail(), customer.getFullName());
    }
}
