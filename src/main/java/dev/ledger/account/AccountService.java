package dev.ledger.account;

import dev.ledger.account.AccountDtos.AccountResponse;
import dev.ledger.account.AccountDtos.LedgerEntryResponse;
import dev.ledger.common.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class AccountService {

    static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTABLE = Set.of("id", "createdAt", "amountCents");

    private final AccountRepository accounts;
    private final LedgerEntryRepository ledgerEntries;
    private final AccountStatusChangeRepository statusChanges;

    public AccountService(AccountRepository accounts, LedgerEntryRepository ledgerEntries,
                          AccountStatusChangeRepository statusChanges) {
        this.accounts = accounts;
        this.ledgerEntries = ledgerEntries;
        this.statusChanges = statusChanges;
    }

    @Transactional
    public AccountResponse create(Long customerId, Account.Type type) {
        return AccountResponse.from(accounts.save(new Account(customerId, type)));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list(Long customerId) {
        return accounts.findByCustomerIdOrderById(customerId).stream().map(AccountResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse get(Long customerId, Long accountId) {
        return AccountResponse.from(owned(customerId, accountId));
    }

    @Transactional
    public AccountResponse deposit(Long customerId, Long accountId, long amountCents) {
        owned(customerId, accountId);
        Account account = accounts.findWithLockById(accountId).orElseThrow(AccountService::notFound);
        requireOpen(account);
        account.credit(amountCents);
        ledgerEntries.save(new LedgerEntry(accountId, LedgerEntry.Direction.CREDIT, amountCents, null));
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse changeStatus(Long customerId, Long accountId, Account.Status newStatus) {
        Account account = owned(customerId, accountId);
        if (account.getStatus() == newStatus) {
            return AccountResponse.from(account);
        }
        if (account.getStatus() == Account.Status.CLOSED) {
            throw new ApiException(HttpStatus.CONFLICT, "Account is closed");
        }
        if (newStatus == Account.Status.CLOSED && account.getBalanceCents() != 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Balance must be zero to close");
        }
        statusChanges.save(new AccountStatusChange(accountId, account.getStatus().name(), newStatus.name(), customerId));
        account.setStatus(newStatus);
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public Page<LedgerEntryResponse> transactions(Long customerId, Long accountId, Instant from, Instant to,
                                                  int page, int size, String sortField, boolean desc) {
        owned(customerId, accountId);
        if (!SORTABLE.contains(sortField)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsortable field: " + sortField);
        }
        if (size < 1 || page < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid page or size");
        }
        PageRequest pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE),
                Sort.by(desc ? Sort.Direction.DESC : Sort.Direction.ASC, sortField));
        Instant effectiveFrom = from != null ? from : Instant.EPOCH;
        Instant effectiveTo = to != null ? to : Instant.now();
        return ledgerEntries.findByAccountIdAndCreatedAtBetween(accountId, effectiveFrom, effectiveTo, pageable)
                .map(LedgerEntryResponse::from);
    }

    public Account owned(Long customerId, Long accountId) {
        // 404, not 403: don't leak which account ids exist.
        return accounts.findByIdAndCustomerId(accountId, customerId).orElseThrow(AccountService::notFound);
    }

    static void requireOpen(Account account) {
        if (account.getStatus() != Account.Status.OPEN) {
            throw new ApiException(HttpStatus.CONFLICT, "Account is " + account.getStatus());
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Account not found");
    }
}
