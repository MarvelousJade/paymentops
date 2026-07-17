package dev.ledger.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByCustomerIdOrderById(Long customerId);

    Optional<Account> findByIdAndCustomerId(Long id, Long customerId);

    /** SELECT ... FOR UPDATE. Callers must lock multiple accounts in ascending-id order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findWithLockById(@Param("id") Long id);
}
