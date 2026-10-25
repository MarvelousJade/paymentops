package dev.ledger.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SettlementRecordRepository extends JpaRepository<SettlementRecord, UUID> {
    List<SettlementRecord> findByPaymentIdOrderByReceivedAt(UUID paymentId);
    List<SettlementRecord> findByPaymentIdAndStatusOrderByReceivedAt(UUID paymentId, SettlementRecord.Status status);
    List<SettlementRecord> findByStatusOrderByReceivedAt(SettlementRecord.Status status);
}
