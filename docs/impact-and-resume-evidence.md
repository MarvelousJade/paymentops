# Impact and resume evidence

## Measurement: complete synthetic HTTP workflow

Problem: payment state, financial effects, settlement and refunds need independently checkable evidence. No valid earlier PaymentOps latency baseline exists. This is an **absolute local result**, not a measured improvement or production impact.

Implementation source: `c8a3b85` (production code identical to `7e14b85` at that measurement checkpoint; later security remediation changed dependencies/configuration, so these timings must not be reattributed to the current dependency set). Java 21.0.12.1+1, Spring Boot 4.0.7, PostgreSQL 16.4, JDBC 42.7.11, Node 22.23.3, Windows 11, Ryzen 5 3600 (12 logical processors), 17,101,787,136 bytes system memory. Single JVM and native database on the same machine; local event transport, SETTLE simulator, simulated webhooks. See [metadata and jar SHA-256](evidence/measurement.json).

Dataset: generated, disposable synthetic data. Each repetition creates two new users, two accounts, one funded 100 CAD source, a 30 CAD payment, and 10+20 CAD refunds. Same payloads/amounts/configuration each time; random IDs vary. Previously retained demo data and new rows grow the database. No cache clearing. One warm-up after JVM restart, then five sequential repetitions; no simultaneous load generator or intentional production traffic. Other host activity was not controlled.

Metric: `workflowMs` starts after user/account setup and includes create/replay/conflict checks, authorization, capture, settlement, first reconciliation, partial/full refund and final balance reads. Final reconciliation/timeline/delivery checks are outside that timing but must pass for the run to count. Outbox poll 250 ms, HTTP poll 50 ms. This is not API-only latency, server processing time, saturation throughput or an SLA.

| Repetition | Workflow ms | Raw evidence |
|---|---:|---|
| 1 | 1459.896 | [run 1](evidence/demo-1.json) |
| 2 | 1186.547 | [run 2](evidence/demo-2.json) |
| 3 | 1221.230 | [run 3](evidence/demo-3.json) |
| 4 | 1202.075 | [run 4](evidence/demo-4.json) |
| 5 | 1205.397 | [run 5](evidence/demo-5.json) |

Median: **1205.397 ms** (sorted sample 3); range **1186.547–1459.896 ms**. Do not calculate p95/p99 from this small sample or call it an optimization. [Warm-up](evidence/demo-warmup.json) excluded; older [smoke](evidence/demo-smoke.json) excluded. Full per-request samples, varying poll/request counts and failure-status checks are retained, not removed as outliers. No speedup/reduction percentage is supported.

Correctness: each run asserted HTTP statuses, key replay/conflicts, ownership denial, final refund state, balances 100/0, one capture timeline event, two refund-completed events and no open exception for its payment. [Direct database checks](evidence/demo-database-checks.txt) independently confirmed exactly 3 postings/3 balanced event-linked pairs, one external settled record and 3,000 refunded cents per measured payment. [After process restart](evidence/demo-after-restart.txt), the same checks passed for all five retained payments. These establish persistence across the tested JVM restart, not arbitrary crash durability or real provider delivery.

Reproduction after README/operator setup:

```bash
node scripts/demo.mjs > warmup.json
for run in 1 2 3 4 5; do node scripts/demo.mjs > "run-$run.json"; done
# JDBC driver from Maven dependency resolution; substitute its actual path and printed UUIDs:
DB_URL=jdbc:postgresql://127.0.0.1:15432/<disposable-demo-db> DB_USER=<user> DB_PASSWORD=<password> \
  java -cp <postgresql-jdbc.jar> scripts/VerifyDemo.java <payment-uuid> ...
```

Stop a JVM running from target before packaging on Windows, or copy the jar outside target before launch. Preserve source SHA and jar hash. No benchmark percentage is claimed and no manual-workflow timing baseline was measured.

## Candidate claims

All “verified” labels below refer to **project evidence**, not confirmed personal contribution. The starting implementation already existed. Owner's personal work/investigation notes have not been supplied; only use first-person/action wording for work you can honestly explain and substantiate.

| Claim | Baseline/problem → change → result | Evidence | Status and candidate wording |
|---|---|---|---|
| Retry safety | Concurrent claim could poison a PostgreSQL transaction → atomic conflict-safe insertion with shared effect transaction → one effect and harmless duplicate; 8 parallel API-service create calls also resolve to one payment/outgoing request | `ab86d88`, `c8a3b85`; EventIdempotencyTest and PaymentOpsFlowTest; bug-001 failing/passing logs; final 48-test run | Verified project capability. “Hardened payment retry handling with PostgreSQL conflict-safe claims and transactional outbox processing; verified single-effect behavior under concurrent event delivery and eight simultaneous create requests.” |
| End-to-end correctness/measurement | No payment-performance baseline → assertion-driven HTTP demo plus direct DB checks → 1.205 s median, five warm local synthetic repetitions, balances restored after full refund | `c8a3b85`, scripts/demo.mjs, scripts/VerifyDemo.java, evidence/measurement.json and demo-1..5; measurement-record commit | Verified absolute local result. “Built a traceable Java/Spring Boot payment workflow with balanced ledger postings, simulated settlement and refunds; completed five warm local synthetic runs at a 1.205-second median with database-verified balance restoration.” Qualify that the initial implementation predates this work; use “validated” rather than “built” if appropriate to personal contribution. |
| Debugging and reliability | Actual rollback-only and missing-settlement regressions → targeted reproductions/repairs → failing tests become passing with preserved evidence | `ab86d88`, `ccff3e4`; journal BUG-001/002; reordered event and transaction rollback tests | Verified repository work, personal contribution unconfirmed. “Reproduced and repaired concurrent deduplication and out-of-order refund/settlement failures using PostgreSQL integration regressions and retained failing/passing evidence.” |

Ready as technical project facts: all three. Ready as personal resume claims: only after confirming personal contribution/study. Resume itself was not edited.

## What is not supported

No adoption, revenue, customer impact, production scale, hours saved, deployment-time reduction, real payment-provider success or current Kafka load result. Historical ledger-only 25,000-request figures remain separately labelled in PERFORMANCE.md and are not proof about this release. Test/commit counts describe verification/scope, not business value. Controlled faults in `docs/debugging` restore previously correct baseline behavior and must not be represented as real production bugs.
