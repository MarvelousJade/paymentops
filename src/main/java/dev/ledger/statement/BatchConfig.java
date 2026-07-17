package dev.ledger.statement;

import dev.ledger.account.Account;
import dev.ledger.account.AccountRepository;
import dev.ledger.account.LedgerEntry;
import dev.ledger.account.LedgerEntryRepository;
import dev.ledger.common.Money;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.data.RepositoryItemReader;
import org.springframework.batch.infrastructure.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemWriter;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.transform.PassThroughLineAggregator;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Configuration
public class BatchConfig {

    private static final Logger log = LoggerFactory.getLogger(BatchConfig.class);

    public static Instant monthStart(YearMonth month) {
        return month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    public static Instant monthEnd(YearMonth month) {
        return month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    public static Path statementPath(String dir, long accountId, YearMonth month) {
        return Path.of(dir, String.valueOf(accountId), month + ".csv");
    }

    // --- Monthly statement: ledger entries of one account/month -> CSV with opening/closing balance ---

    @Bean
    Job statementJob(JobRepository jobRepository, Step statementStep) {
        return new JobBuilder("statementJob", jobRepository).start(statementStep).build();
    }

    @Bean
    Step statementStep(JobRepository jobRepository, PlatformTransactionManager txManager,
                       RepositoryItemReader<LedgerEntry> statementReader, FlatFileItemWriter<String> statementWriter) {
        return new StepBuilder("statementStep", jobRepository)
                .<LedgerEntry, String>chunk(100, txManager)
                .reader(statementReader)
                .processor(entry -> "%s,%s,%s,%s".formatted(
                        entry.getCreatedAt(), entry.getDirection(),
                        Money.toDecimalString(entry.getAmountCents()),
                        entry.getTransferId() == null ? "deposit" : "transfer-" + entry.getTransferId()))
                .writer(statementWriter)
                .build();
    }

    @Bean
    @StepScope
    RepositoryItemReader<LedgerEntry> statementReader(LedgerEntryRepository ledgerEntries,
                                                     @Value("#{jobParameters['accountId']}") Long accountId,
                                                     @Value("#{jobParameters['month']}") String month) {
        YearMonth yearMonth = YearMonth.parse(month);
        return new RepositoryItemReaderBuilder<LedgerEntry>()
                .name("statementReader")
                .repository(ledgerEntries)
                .methodName("findByAccountIdAndCreatedAtBetween")
                .arguments(List.of(accountId, monthStart(yearMonth), monthEnd(yearMonth)))
                .sorts(Map.of("createdAt", Sort.Direction.ASC, "id", Sort.Direction.ASC))
                .pageSize(100)
                .build();
    }

    @Bean
    @StepScope
    FlatFileItemWriter<String> statementWriter(LedgerEntryRepository ledgerEntries,
                                               @Value("${ledger.statements.dir}") String dir,
                                               @Value("#{jobParameters['accountId']}") Long accountId,
                                               @Value("#{jobParameters['month']}") String month) {
        YearMonth yearMonth = YearMonth.parse(month);
        long opening = ledgerEntries.ledgerBalanceBefore(accountId, monthStart(yearMonth));
        long closing = ledgerEntries.ledgerBalanceBefore(accountId, monthEnd(yearMonth));
        return new FlatFileItemWriterBuilder<String>()
                .name("statementWriter")
                .resource(new FileSystemResource(statementPath(dir, accountId, yearMonth)))
                .lineAggregator(new PassThroughLineAggregator<>())
                .headerCallback(w -> w.write("timestamp,direction,amount,reference\nopening_balance," + Money.toDecimalString(opening)))
                .footerCallback(w -> w.write("closing_balance," + Money.toDecimalString(closing)))
                .shouldDeleteIfExists(true)
                .build();
    }

    // --- Nightly reconciliation: ledger sum vs materialized balance, alarm on drift ---

    @Bean
    Job reconciliationJob(JobRepository jobRepository, Step reconciliationStep) {
        return new JobBuilder("reconciliationJob", jobRepository).start(reconciliationStep).build();
    }

    @Bean
    Step reconciliationStep(JobRepository jobRepository, PlatformTransactionManager txManager,
                            AccountRepository accounts, LedgerEntryRepository ledgerEntries, MeterRegistry meters) {
        // ponytail: single-pass tasklet over all accounts; make it a chunked step when account count outgrows one transaction.
        return new StepBuilder("reconciliationStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    int drifted = 0;
                    for (Account account : accounts.findAll()) {
                        long ledger = ledgerEntries.ledgerBalance(account.getId());
                        if (ledger != account.getBalanceCents()) {
                            drifted++;
                            log.warn("Reconciliation drift: account={} ledger={} materialized={}",
                                    account.getId(), ledger, account.getBalanceCents());
                        }
                    }
                    meters.counter("ledger.reconciliation.drift").increment(drifted);
                    log.info("Reconciliation done: {} accounts drifted", drifted);
                    return RepeatStatus.FINISHED;
                }, txManager)
                .build();
    }
}
