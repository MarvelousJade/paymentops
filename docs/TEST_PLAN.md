# PaymentOps test plan

## Automated coverage

`PaymentOpsFlowTest`:

- repeated requests using one idempotency key return one payment;
- deterministic risk approval and per-transaction rejection;
- authorization, capture, balanced posting, settlement, and reconciliation;
- replay of the same capture event produces no duplicate posting;
- partial refund creates one reverse debit/credit posting;
- invalid transition returns `409` and a stable error code;
- missing, duplicate, and amount-mismatch classifications;
- corrected records resolve prior missing evidence without deleting it;
- missing authentication/idempotency headers and merchant access to operations
  are rejected.

Existing suites:

- 60 concurrent transfers conserve money and never overdraft;
- a 12-thread idempotency race creates one transfer;
- ledger/materialized balances agree;
- async SQS redelivery settles once;
- expired/invalid auth and refresh-token rotation behavior;
- rate-limit rejection;
- statement and account reconciliation batch jobs.

`EventIdempotencyTest` verifies a real PostgreSQL lock-observed duplicate race and rollback of claim plus effect on failure. The reordered early-refund test covers settlement evidence and post-refund reconciliation/capture replay.

## Failure and recovery matrix (proposed drills unless noted)

| Scenario | Injection | Expected evidence |
|---|---|---|
| Kafka unavailable | Stop broker, submit payment | API state + pending outbox persist; publish retries |
| Replay (locally verified; not a Kafka crash drill) | Route same event twice | Duplicate metric/warning; one posting |
| Delayed settlement | `SETTLEMENT_BEHAVIOR=DELAY` | Delayed timeline; missing exception; later resolution |
| Duplicate settlement | `SETTLEMENT_BEHAVIOR=DUPLICATE` | Duplicate exception with both records |
| Poison event | Publish incompatible known event | Indexed retries then dead-letter topic |
| Consumer backlog | Pause consumer, create payments, resume | Lag rises then drains; effects remain unique |
| Webhook failure | Configure unavailable URL | Three attempts, delivery DLT, retained history |

## Security checks

- unauthenticated modifying calls return `401`;
- missing scope/role returns `403`;
- missing idempotency key returns `400`;
- expired tokens are rejected by the JWT decoder;
- only a source-account owner can create a payment;
- payment tokens outside the synthetic pattern fail validation;
- logs are scanned for test bearer/token values;
- Dependency-Check and Trivy fail for configured high/critical findings.

## Schema and contract checks

CI parses every JSON Schema and Compose registers them into a
backward-compatible Schema Registry. Contract tests must be added before a
producer adds/removes fields. Breaking changes require a new `.v2` topic and a
migration period.

## Performance procedure

1. Start the stack with risk/rate limits sized for the intended load.
2. Run `k6 run k6/paymentops.js -e BASE=http://localhost:8080`.
3. Preserve the k6 summary, application SHA, hardware, configuration, and
   dashboard snapshots.
4. Report p50, p95, p99, throughput, errors, consumer lag, duplicate postings,
   and reconciliation outcomes only from that completed run.
5. Query all completed test payments: every one must reconcile or have an
   explained exception.
