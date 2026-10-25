package dev.ledger;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@OpenAPIDefinition(info = @Info(
        title = "PaymentOps API",
        version = "1.0",
        description = "Event-driven payment operations: authorization, capture, refunds, transactional outbox, balanced ledger posting, settlement, reconciliation, and signed webhooks."))

@SpringBootApplication
public class PaymentOpsApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaymentOpsApplication.class, args);
	}

}
