# Event catalog

All topics contain the versioned envelope in
`src/main/resources/schemas/event-envelope-v1.schema.json`.

```json
{
  "eventId": "UUID",
  "eventType": "payment.requested.v1",
  "occurredAt": "UTC timestamp",
  "correlationId": "UUID",
  "causationId": "UUID or null",
  "aggregateId": "payment UUID",
  "schemaVersion": 1,
  "producer": "payment-api",
  "payload": {}
}
```

| Event | Producer | Primary consumer/effect |
|---|---|---|
| `payment.requested.v1` | Payment API | Risk rules decide approve/review/reject |
| `payment.risk-decided.v1` | Risk rules | Analytics/audit |
| `payment.approved.v1` | Risk rules | Merchant notification |
| `payment.rejected.v1` | Risk rules | Merchant notification |
| `payment.capture-requested.v1` | Payment API | Balanced ledger posting |
| `payment.capture-posted.v1` | Ledger | Settlement simulator |
| `payment.cancelled.v1` | Payment API | Merchant notification |
| `payment.failed.v1` | Ledger | Merchant notification |
| `payment.settled.v1` | Settlement | Reconciliation and notification |
| `payment.settlement-delayed.v1` | Settlement | Operations visibility |
| `payment.settlement-failed.v1` | Settlement | Merchant notification |
| `payment.refund-requested.v1` | Payment API | Reverse ledger posting |
| `payment.refunded.v1` | Ledger | Merchant notification |
| `payment.refund-failed.v1` | Ledger | Merchant notification |
| `reconciliation.mismatch.v1` | Reconciliation | Operations notification |
| `notification.dead-letter.v1` | Notification | Operator investigation |

Kafka consumers use indexed `.retry` topics and a `.dead-letter` suffix after
four attempts. Database-outbox delivery uses exponential retry and
`DEAD_LETTER` state after the configured maximum. Operators may replay only
events already in that state.

Schema Registry starts in `BACKWARD` compatibility mode. CI parses every
schema, and Compose registers the envelope, payment-requested payload, and
settlement-record schemas before the application starts.
