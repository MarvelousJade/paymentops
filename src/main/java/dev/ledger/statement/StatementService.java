package dev.ledger.statement;

import dev.ledger.account.AccountService;
import dev.ledger.common.ApiException;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@Service
public class StatementService {

    private final AccountService accountService;
    private final JobOperator jobOperator;
    private final Job statementJob;
    private final String dir;

    public StatementService(AccountService accountService, JobOperator jobOperator,
                            @Qualifier("statementJob") Job statementJob,
                            @Value("${ledger.statements.dir}") String dir) {
        this.accountService = accountService;
        this.jobOperator = jobOperator;
        this.statementJob = statementJob;
        this.dir = dir;
    }

    /** Generates (or regenerates) the statement and returns the CSV path. Ledger is append-only, so output is deterministic. */
    public Path statement(Long customerId, Long accountId, String month) {
        accountService.owned(customerId, accountId);
        YearMonth yearMonth;
        try {
            yearMonth = YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Month must be yyyy-MM");
        }
        if (yearMonth.isAfter(YearMonth.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Statement month is in the future");
        }

        Path path = BatchConfig.statementPath(dir, accountId, yearMonth);
        if (!Files.exists(path)) {
            try {
                jobOperator.start(statementJob, new JobParametersBuilder()
                        .addLong("accountId", accountId)
                        .addString("month", yearMonth.toString())
                        .addLong("requestedAt", System.currentTimeMillis()) // fresh job instance per request
                        .toJobParameters());
            } catch (Exception e) {
                throw new IllegalStateException("Statement job failed", e);
            }
        }
        if (!Files.exists(path)) {
            throw new IllegalStateException("Statement job produced no file for " + path);
        }
        return path;
    }
}
