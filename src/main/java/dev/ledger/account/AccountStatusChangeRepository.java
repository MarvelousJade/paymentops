package dev.ledger.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountStatusChangeRepository extends JpaRepository<AccountStatusChange, Long> {

    List<AccountStatusChange> findByAccountIdOrderByChangedAt(Long accountId);
}
