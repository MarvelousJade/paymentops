package dev.ledger;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@OpenAPIDefinition(info = @Info(
        title = "Ledger Service API",
        version = "1.0",
        description = "Payments ledger: double-entry bookkeeping, idempotent transfers, async settlement via SQS/Lambda. Register, then Authorize with the returned access token."))

@SpringBootApplication
public class LedgerServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(LedgerServiceApplication.class, args);
	}

}
