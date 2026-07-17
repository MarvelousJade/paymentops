package dev.ledger.account;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    Page<LedgerEntry> findByAccountIdAndCreatedAtBetween(Long accountId, Instant from, Instant to, Pageable pageable);

    @Query("select coalesce(sum(e.amountCents), 0) from LedgerEntry e where e.accountId = :accountId and e.direction = :direction")
    long sumByDirection(@Param("accountId") Long accountId, @Param("direction") LedgerEntry.Direction direction);

    /** Balance according to the ledger alone: credits minus debits. Reconciliation compares this to accounts.balance_cents. */
    default long ledgerBalance(Long accountId) {
        return sumByDirection(accountId, LedgerEntry.Direction.CREDIT) - sumByDirection(accountId, LedgerEntry.Direction.DEBIT);
    }
}
