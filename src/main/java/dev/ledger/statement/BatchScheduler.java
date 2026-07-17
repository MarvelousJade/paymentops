package dev.ledger.statement;

import dev.ledger.account.Account;
import dev.ledger.account.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

/** Local scheduling via @Scheduled; EventBridge Scheduler is the cloud-native swap (see DECISIONS.md). */
@Component
@EnableScheduling
public class BatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(BatchScheduler.class);

    private final JobOperator jobOperator;
    private final Job statementJob;
    private final Job reconciliationJob;
    private final AccountRepository accounts;

    public BatchScheduler(JobOperator jobOperator, @Qualifier("statementJob") Job statementJob,
                          @Qualifier("reconciliationJob") Job reconciliationJob, AccountRepository accounts) {
        this.jobOperator = jobOperator;
        this.statementJob = statementJob;
        this.reconciliationJob = reconciliationJob;
        this.accounts = accounts;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void nightlyReconciliation() throws Exception {
        jobOperator.start(reconciliationJob, new JobParametersBuilder()
                .addLong("runAt", System.currentTimeMillis())
                .toJobParameters());
    }

    @Scheduled(cron = "0 30 2 1 * *")
    public void monthlyStatements() {
        YearMonth previous = YearMonth.now().minusMonths(1);
        for (Account account : accounts.findAll()) {
            try {
                jobOperator.start(statementJob, new JobParametersBuilder()
                        .addLong("accountId", account.getId())
                        .addString("month", previous.toString())
                        .addLong("requestedAt", System.currentTimeMillis())
                        .toJobParameters());
            } catch (Exception e) {
                log.error("Statement generation failed for account {}", account.getId(), e);
            }
        }
    }
}
