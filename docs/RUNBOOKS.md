# PaymentOps operational runbooks

## Duplicate delivery or posting alert

Trigger: `paymentops_events_duplicate_total` increases, a duplicate warning
appears, or reconciliation reports a duplicate financial effect.

1. Capture the `eventId`, `correlationId`, consumer, topic, partition, and
   offset from the structured warning.
2. Search the operations console by correlation ID and compare the timeline
   with `processed_events`.
3. Query `payment_ledger_postings` by `source_event_id`. The expected count is
   zero or one; verify its debit and credit entry IDs are distinct and amounts
   equal.
4. Verify the two involved account balances equal credits minus debits.
5. Run reconciliation. If there is no unexplained exception, classify the
   duplicate as safely absorbed and retain the evidence.
6. If a second posting exists, stop the affected consumer, open a severity-1
   incident, preserve database/Kafka evidence, and correct through compensating
   entries—never update/delete ledger history.
7. Resume only after the replay test passes and lag drains without new
   duplicate effects.

## Outbox backlog

Trigger: pending outbox gauge grows for 10 minutes or the oldest row is older
than the service objective.

1. Check Kafka and Schema Registry health, broker disk, authentication, and
   producer error logs.
2. Do not delete outbox rows. Restore the dependency or deploy the last known
   good producer configuration.
3. Watch publish rate and pending depth. At-least-once replay is expected;
   consumer idempotency must absorb it.
4. For `DEAD_LETTER` rows, inspect `last_error` and payload schema. Correct the
   poison data or deploy a compatible consumer before using the replay API.

## Kafka consumer lag

1. Identify group/topic/partition in the Grafana Kafka lag panel.
2. Check handler latency, database pool saturation, lock waits, and retry
   topics.
3. Scale instances only if handlers remain idempotent and partition count
   supports it.
4. Never reset offsets forward to hide lag. If an offset change is approved,
   record old/new offsets and reconcile the affected time range.

## Reconciliation exception

1. Acknowledge the exception with the incident/ticket identifier.
2. Compare immutable internal posting IDs with the raw external record.
3. Classify timing, duplication, missing side, or amount mismatch.
4. Import a corrected synthetic settlement record or add compensating ledger
   entries through an approved workflow. Never edit evidence.
5. Rerun reconciliation; resolve with notes only after underlying values agree.
   The action history remains append-only.

## Webhook dead letter

1. Verify the configured endpoint without exposing the signing secret.
2. Inspect status code/timeout and attempt timestamps. Payload history excludes
   the secret; the signature itself is not returned by the API.
3. Delivery requeue is not implemented. Replaying an already processed notification event will be deduplicated and does **not** reset its dead-letter delivery. Preserve history; a reviewed requeue API with its own audit/authorization is follow-up work.
4. For future real merchant delivery, require event-ID idempotency at the receiver; a delivery history row alone does not establish a merchant-side effect.

## Useful health checks

```text
GET /actuator/health
GET /actuator/prometheus
GET /api/v1/operations/health
GET /api/v1/operations/payments?correlationId={uuid}
GET /api/v1/operations/reconciliation/exceptions?status=OPEN
```
