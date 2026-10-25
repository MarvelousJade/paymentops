package dev.ledger.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        UUID correlationId = parseOrCreate(request.getHeader(CorrelationIds.HEADER));
        CorrelationIds.set(correlationId);
        response.setHeader(CorrelationIds.HEADER, correlationId.toString());
        try {
            chain.doFilter(request, response);
        } finally {
            CorrelationIds.clear();
        }
    }

    private static UUID parseOrCreate(String value) {
        try {
            return value == null || value.isBlank() ? UUID.randomUUID() : UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return UUID.randomUUID();
        }
    }
}
