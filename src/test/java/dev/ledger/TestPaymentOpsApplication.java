package dev.ledger;

import org.springframework.boot.SpringApplication;

public class TestPaymentOpsApplication {

	public static void main(String[] args) {
		SpringApplication.from(PaymentOpsApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
