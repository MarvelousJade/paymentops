package dev.ledger.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_ledger_postings")
public class PaymentLedgerPosting {

    @Id
    private UUID id;
    private UUID paymentId;
    private String operationKey;
    private UUID sourceEventId;
    private Long debitEntryId;
    private Long creditEntryId;
    private long amountCents;
    @Column(insertable = false, updatable = false)
    private Instant postedAt;

    protected PaymentLedgerPosting() {
    }

    public PaymentLedgerPosting(UUID paymentId, String operationKey, UUID sourceEventId,
                                Long debitEntryId, Long creditEntryId, long amountCents) {
        this.id = UUID.randomUUID();
        this.paymentId = paymentId;
        this.operationKey = operationKey;
        this.sourceEventId = sourceEventId;
        this.debitEntryId = debitEntryId;
        this.creditEntryId = creditEntryId;
        this.amountCents = amountCents;
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public String getOperationKey() { return operationKey; }
    public UUID getSourceEventId() { return sourceEventId; }
    public Long getDebitEntryId() { return debitEntryId; }
    public Long getCreditEntryId() { return creditEntryId; }
    public long getAmountCents() { return amountCents; }
    public Instant getPostedAt() { return postedAt; }
}
