package dev.ledger.transfer;

import dev.ledger.account.Account;
import dev.ledger.account.AccountRepository;
import dev.ledger.account.LedgerEntry;
import dev.ledger.account.LedgerEntryRepository;
import dev.ledger.common.ApiException;
import dev.ledger.transfer.TransferDtos.TransferResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TransferService {

    private final TransferRepository transfers;
    private final AccountRepository accounts;
    private final LedgerEntryRepository ledgerEntries;
    private final RateLimiter rateLimiter;
    private final TransactionTemplate tx;

    public TransferService(TransferRepository transfers, AccountRepository accounts,
                           LedgerEntryRepository ledgerEntries, RateLimiter rateLimiter, TransactionTemplate tx) {
        this.transfers = transfers;
        this.accounts = accounts;
        this.ledgerEntries = ledgerEntries;
        this.rateLimiter = rateLimiter;
        this.tx = tx;
    }

    /**
     * Two transactions on purpose. Tx1 claims the (customer, idempotency key) pair by inserting the
     * PENDING row — the unique constraint is the idempotency guarantee, so a concurrent duplicate
     * dies at commit without poisoning the settlement transaction. Tx2 settles. A replay at any
     * point returns the already-claimed transfer in whatever state it reached.
     */
    public TransferResponse create(Long customerId, String idempotencyKey, Long fromAccountId,
                                   Long toAccountId, long amountCents) {
        rateLimiter.acquire(customerId);

        var existing = transfers.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey);
        if (existing.isPresent()) {
            return TransferResponse.from(existing.get());
        }

        if (fromAccountId.equals(toAccountId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot transfer to the same account");
        }

        Long transferId;
        try {
            transferId = tx.execute(status -> {
                // 404, not 403: don't leak which account ids exist.
                accounts.findByIdAndCustomerId(fromAccountId, customerId)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
                if (!accounts.existsById(toAccountId)) {
                    throw new ApiException(HttpStatus.NOT_FOUND, "Destination account not found");
                }
                return transfers.save(new Transfer(idempotencyKey, customerId, fromAccountId, toAccountId, amountCents)).getId();
            });
        } catch (DataIntegrityViolationException e) {
            // Lost the claim race; the winner's row is the result.
            return TransferResponse.from(transfers.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey)
                    .orElseThrow(() -> e));
        }

        return tx.execute(status -> TransferResponse.from(settle(transferId)));
    }

    private Transfer settle(Long transferId) {
        Transfer transfer = transfers.findById(transferId).orElseThrow();

        // Deadlock avoidance: always lock both accounts in ascending-id order, whichever
        // direction the money flows. Two opposing transfers then queue instead of deadlocking.
        long firstId = Math.min(transfer.getFromAccountId(), transfer.getToAccountId());
        long secondId = Math.max(transfer.getFromAccountId(), transfer.getToAccountId());
        Account first = accounts.findWithLockById(firstId).orElseThrow();
        Account second = accounts.findWithLockById(secondId).orElseThrow();
        Account from = first.getId().equals(transfer.getFromAccountId()) ? first : second;
        Account to = from == first ? second : first;

        if (from.getStatus() != Account.Status.OPEN) {
            transfer.fail("Source account is " + from.getStatus());
        } else if (to.getStatus() != Account.Status.OPEN) {
            transfer.fail("Destination account is " + to.getStatus());
        } else if (from.getBalanceCents() < transfer.getAmountCents()) {
            transfer.fail("Insufficient funds");
        } else {
            from.debit(transfer.getAmountCents());
            to.credit(transfer.getAmountCents());
            ledgerEntries.save(new LedgerEntry(from.getId(), LedgerEntry.Direction.DEBIT,
                    transfer.getAmountCents(), transfer.getId()));
            ledgerEntries.save(new LedgerEntry(to.getId(), LedgerEntry.Direction.CREDIT,
                    transfer.getAmountCents(), transfer.getId()));
            transfer.complete();
        }
        return transfer;
    }

    public TransferResponse get(Long customerId, Long transferId) {
        return transfers.findByIdAndCustomerId(transferId, customerId)
                .map(TransferResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Transfer not found"));
    }
}
