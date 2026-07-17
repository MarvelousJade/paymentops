package dev.ledger.transfer;

import dev.ledger.TestcontainersConfiguration;
import dev.ledger.account.Account;
import dev.ledger.account.AccountRepository;
import dev.ledger.account.AccountService;
import dev.ledger.account.LedgerEntryRepository;
import dev.ledger.customer.Customer;
import dev.ledger.customer.CustomerRepository;
import dev.ledger.transfer.TransferDtos.TransferResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The test that matters: N parallel transfers among 3 accounts, mixed directions.
 * Proves the pessimistic locks with ascending-id ordering neither deadlock nor lose money.
 */
@SpringBootTest(properties = "ledger.rate-limit.transfers-per-minute=100000")
@Import(TestcontainersConfiguration.class)
class MoneyConservationTest {

    private static final long SEED_CENTS = 100_000; // 1000.00 per account
    private static final int TRANSFERS = 60;
    private static final int THREADS = 16;

    @Autowired
    TransferService transferService;

    @Autowired
    AccountService accountService;

    @Autowired
    AccountRepository accounts;

    @Autowired
    LedgerEntryRepository ledgerEntries;

    @Autowired
    CustomerRepository customers;

    private Long customerId;

    private long[] seedAccounts(int count) {
        Customer customer = customers.save(new Customer(
                "cc" + UUID.randomUUID().toString().substring(0, 8) + "@x.dev", "n/a", "Concurrency Tester"));
        customerId = customer.getId();
        long[] ids = new long[count];
        for (int i = 0; i < count; i++) {
            ids[i] = accountService.create(customerId, Account.Type.CHEQUING).id();
            accountService.deposit(customerId, ids[i], SEED_CENTS);
        }
        return ids;
    }

    @Test
    void parallelTransfersConserveMoneyAndNeverOverdraw() throws Exception {
        long[] ids = seedAccounts(3);

        List<Callable<TransferResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < TRANSFERS; i++) {
            tasks.add(() -> {
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                int fromIdx = rnd.nextInt(ids.length);
                int toIdx = (fromIdx + 1 + rnd.nextInt(ids.length - 1)) % ids.length;
                long amount = rnd.nextLong(100, 5_000);
                return transferService.create(customerId, UUID.randomUUID().toString(),
                        ids[fromIdx], ids[toIdx], amount);
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            // A deadlock would blow this timeout: with unordered locks, A->B and B->A wait on each other forever.
            List<Future<TransferResponse>> results = pool.invokeAll(tasks, 60, TimeUnit.SECONDS);

            for (Future<TransferResponse> future : results) {
                assertThat(future.isCancelled()).as("transfer timed out — possible deadlock").isFalse();
                TransferResponse response = future.get();
                assertThat(response.status()).isIn(Transfer.Status.COMPLETED, Transfer.Status.FAILED);
                if (response.status() == Transfer.Status.FAILED) {
                    assertThat(response.failureReason()).isEqualTo("Insufficient funds");
                }
            }
        } finally {
            pool.shutdownNow();
        }

        long total = 0;
        for (long id : ids) {
            Account account = accounts.findById(id).orElseThrow();
            assertThat(account.getBalanceCents()).as("no overdraft on account %d", id).isGreaterThanOrEqualTo(0);
            // The materialized balance must agree with the append-only ledger.
            assertThat(ledgerEntries.ledgerBalance(id)).as("ledger drift on account %d", id)
                    .isEqualTo(account.getBalanceCents());
            total += account.getBalanceCents();
        }
        assertThat(total).as("money is conserved").isEqualTo(ids.length * SEED_CENTS);
    }

    @Test
    void concurrentSameKeyRequestsCreateExactlyOneTransfer() throws Exception {
        long[] ids = seedAccounts(2);
        String key = UUID.randomUUID().toString();

        List<Callable<TransferResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            tasks.add(() -> transferService.create(customerId, key, ids[0], ids[1], 2_500));
        }

        ExecutorService pool = Executors.newFixedThreadPool(12);
        List<TransferResponse> responses = new ArrayList<>();
        try {
            for (Future<TransferResponse> future : pool.invokeAll(tasks, 60, TimeUnit.SECONDS)) {
                responses.add(future.get());
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(responses).extracting(TransferResponse::id).containsOnly(responses.get(0).id());
        // Money moved exactly once, no matter how many racers carried the same key.
        assertThat(accounts.findById(ids[0]).orElseThrow().getBalanceCents()).isEqualTo(SEED_CENTS - 2_500);
        assertThat(accounts.findById(ids[1]).orElseThrow().getBalanceCents()).isEqualTo(SEED_CENTS + 2_500);
    }
}
