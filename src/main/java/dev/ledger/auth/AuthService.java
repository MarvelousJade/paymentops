package dev.ledger.auth;

import dev.ledger.auth.AuthDtos.TokenResponse;
import dev.ledger.common.ApiException;
import dev.ledger.common.JwtProps;
import dev.ledger.customer.Customer;
import dev.ledger.customer.CustomerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CustomerRepository customers;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtProps jwtProps;

    public AuthService(CustomerRepository customers, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder, JwtProps jwtProps) {
        this.customers = customers;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.jwtProps = jwtProps;
    }

    @Transactional
    public TokenResponse register(String email, String password, String fullName) {
        if (customers.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        }
        Customer customer = customers.save(new Customer(email, passwordEncoder.encode(password), fullName));
        return issueTokens(customer);
    }

    @Transactional
    public TokenResponse login(String email, String password) {
        Customer customer = customers.findByEmail(email)
                .orElseThrow(AuthService::badCredentials);
        if (!passwordEncoder.matches(password, customer.getPasswordHash())) {
            throw badCredentials();
        }
        return issueTokens(customer);
    }

    // noRollbackFor: the 401 thrown on token reuse must NOT roll back the family revocation.
    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse refresh(String rawToken) {
        RefreshToken token = refreshTokens.findByTokenHash(sha256(rawToken))
                .orElseThrow(AuthService::badCredentials);
        if (token.isRevoked()) {
            // Reuse of a rotated token means the token may be stolen: kill the whole family.
            refreshTokens.revokeAllForCustomer(token.getCustomerId(), Instant.now());
            throw badCredentials();
        }
        if (token.isExpired()) {
            throw badCredentials();
        }
        token.revoke();
        Customer customer = customers.findById(token.getCustomerId())
                .orElseThrow(AuthService::badCredentials);
        return issueTokens(customer);
    }

    private TokenResponse issueTokens(Customer customer) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(customer.getId()))
                .claim("email", customer.getEmail())
                .issuedAt(now)
                .expiresAt(now.plus(jwtProps.accessTtl()))
                .build();
        String accessToken = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        refreshTokens.save(new RefreshToken(customer.getId(), sha256(refreshToken),
                now.plus(jwtProps.refreshTtl())));

        return new TokenResponse(accessToken, refreshToken, jwtProps.accessTtl().toSeconds());
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException badCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }
}
