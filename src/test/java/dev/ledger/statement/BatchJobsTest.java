package dev.ledger.statement;

import dev.ledger.TestcontainersConfiguration;
import dev.ledger.account.Account;
import dev.ledger.account.AccountRepository;
import dev.ledger.account.AccountService;
import dev.ledger.auth.AuthDtos.RegisterRequest;
import dev.ledger.auth.AuthDtos.TokenResponse;
import dev.ledger.customer.Customer;
import dev.ledger.customer.CustomerRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "ledger.statements.dir=target/test-statements")
@Import(TestcontainersConfiguration.class)
class BatchJobsTest {

    @LocalServerPort
    int port;

    @Autowired
    JobOperator jobOperator;

    @Autowired
    @Qualifier("reconciliationJob")
    Job reconciliationJob;

    @Autowired
    AccountService accountService;

    @Autowired
    AccountRepository accounts;

    @Autowired
    CustomerRepository customers;

    @Autowired
    MeterRegistry meters;

    RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void statementCsvHasOpeningEntriesAndClosing() {
        RestTestClient client = client();
        String email = "s" + UUID.randomUUID().toString().substring(0, 8) + "@x.dev";
        String token = client.post().uri("/auth/register")
                .body(new RegisterRequest(email, "password123", "Statement Tester"))
                .exchange().expectStatus().isCreated()
                .expectBody(TokenResponse.class).returnResult().getResponseBody().accessToken();

        Map<String, Object> account = client.post().uri("/accounts")
                .header("Authorization", "Bearer " + token)
                .body(Map.of("type", "SAVINGS"))
                .exchange().expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody();
        long accountId = ((Number) account.get("id")).longValue();

        for (String amount : new String[]{"10.00", "20.00"}) {
            client.post().uri("/accounts/" + accountId + "/deposits")
                    .header("Authorization", "Bearer " + token)
                    .body(Map.of("amount", amount))
                    .exchange().expectStatus().isCreated();
        }

        String month = YearMonth.now(ZoneOffset.UTC).toString();
        String csv = client.get().uri("/accounts/" + accountId + "/statements/" + month)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("text/csv")
                .expectBody(String.class)
                .returnResult().getResponseBody();

        assertThat(csv).startsWith("timestamp,direction,amount,reference");
        assertThat(csv).contains("opening_balance,0.00");
        assertThat(csv).contains("CREDIT,10.00,deposit");
        assertThat(csv).contains("CREDIT,20.00,deposit");
        assertThat(csv).endsWith("closing_balance,30.00");

        // Bad month format and future months are rejected.
        client.get().uri("/accounts/" + accountId + "/statements/2026-13")
                .header("Authorization", "Bearer " + token)
                .exchange().expectStatus().isBadRequest();
        client.get().uri("/accounts/" + accountId + "/statements/" + YearMonth.now().plusMonths(2))
                .header("Authorization", "Bearer " + token)
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void reconciliationFlagsDriftedAccount() throws Exception {
        Customer customer = customers.save(new Customer(
                "r" + UUID.randomUUID().toString().substring(0, 8) + "@x.dev", "n/a", "Recon Tester"));
        long accountId = accountService.create(customer.getId(), Account.Type.CHEQUING).id();
        accountService.deposit(customer.getId(), accountId, 5_000);

        // Corrupt the materialized balance without a ledger entry — exactly what reconciliation must catch.
        Account account = accounts.findById(accountId).orElseThrow();
        account.credit(1);
        accounts.save(account);

        double before = meters.counter("ledger.reconciliation.drift").count();
        JobExecution execution = jobOperator.start(reconciliationJob,
                new JobParametersBuilder().addLong("runAt", System.nanoTime()).toJobParameters());

        assertThat(execution.getStatus().toString()).isEqualTo("COMPLETED");
        assertThat(meters.counter("ledger.reconciliation.drift").count()).isGreaterThan(before);
    }
}
