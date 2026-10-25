# Decisions

> PaymentOps extension note (2026-07): the original decisions below describe
> the ledger-only baseline. PaymentOps intentionally supersedes the earlier
> Kafka/microservices out-of-scope boundary with a modular-monolith event
> architecture, transactional outbox, optional Kafka transport, settlement,
> reconciliation, notification, and operations modules. See
> `docs/ARCHITECTURE.md` for the current system.

One paragraph per non-obvious choice, newest last.

## 1. Modular monolith, not microservices

Single Spring Boot app, package-by-feature (`auth / customer / account / transfer /
statement / common`). A ledger's core invariant — money is conserved across a
debit and a credit — lives inside one ACID transaction, which one service and one
database give for free. Splitting accounts and transfers into services would turn
that invariant into a distributed-saga problem with no compensating benefit at
this scale. The package boundaries keep the seams visible; if one module ever
needs independent scaling, it can be extracted along its package line.

## 2. Spring Boot 4.0.x, not 3.5.x

The plan preferred Boot 3.5 with Boot 4 allowed "if springdoc and Spring Cloud AWS
support it". Spring Initializr no longer generates 3.5 projects, springdoc 3.x
targets Boot 4, and Spring Cloud AWS is only needed for the async-settlement
phase. Starting on 4.0.x avoids a guaranteed near-term major upgrade on a resume
project whose whole point is to look current.

## 3. Money is BIGINT minor units, never double, not NUMERIC

`double` cannot represent 0.10 and accumulates rounding error — disqualifying for
money. `NUMERIC` is exact but pushes scale/rounding decisions into every query
and driver conversion. BIGINT cents make every arithmetic operation exact integer
math, comparisons trivial, and overflow detectable (`longValueExact`). The API
speaks decimal strings ("12.34") so clients never see the representation. Single
currency (CAD) is explicit scope: multi-currency adds a currency column and
per-currency scale rules — out of scope until something needs it.

## 4. Ledger is source of truth; balance_cents is materialized

Every movement writes immutable double-entry `ledger_entries`; `accounts.balance_cents`
is a convenience so reads don't sum history. The tradeoff is drift risk if a code
path ever touches one without the other — accepted because both writes share one
transaction, and the nightly reconciliation job (Phase 5) recomputes
credits − debits per account and alarms on any mismatch.

## 5. Pessimistic locking with ascending-id lock order

Transfers `SELECT ... FOR UPDATE` both accounts. Without a fixed order, A→B and
B→A grab locks in opposite sequences and deadlock (Postgres kills one after
`deadlock_timeout`). Locking min(id) then max(id) makes every transfer acquire in
the same global order, so opposing transfers serialize instead. Optimistic
`@Version` retry was the alternative — fine at low contention, but hot accounts
(payroll, merchant) would retry-storm; the bank-style answer is short pessimistic
critical sections. Verified by `MoneyConservationTest`: 60 parallel mixed-direction
transfers, total conserved, no negative balance, ledger equals materialized balance.

## 6. Idempotency: claim in one transaction, settle in another

`POST /transfers` first inserts the PENDING row (claiming the
`(customer_id, idempotency_key)` unique slot), commits, then settles in a second
transaction. One transaction can't do both: a duplicate-key violation poisons the
whole Postgres transaction, taking the settlement down with it. Losing claimants
catch the violation and return the winner's row — the Stripe replay contract:
same key, same transfer, whatever state it reached. Crash between claim and
settle leaves a PENDING row a client sees via `GET /transfers/{id}` — the async
settlement phase makes PENDING a first-class state anyway.

## 7. Batch scheduling via @Scheduled, not EventBridge

Statements and reconciliation are Spring Batch jobs (restartable, instrumented,
chunked reads) triggered by in-process `@Scheduled` crons. The cloud-native
alternative is EventBridge Scheduler hitting an endpoint or launching a task —
right when there are multiple instances (in-process cron would fire once per
instance) or when the app scales to zero. One Railway instance makes @Scheduled
the honest choice; the job code wouldn't change, only the trigger.

## 8. Two idempotency layers, on purpose

Client layer: the `Idempotency-Key` unique constraint in Postgres makes retried
`POST /transfers` return the original transfer. Message layer: the Lambda does a
DynamoDB conditional put on the SQS message id (TTL 1h) because SQS standard
queues are at-least-once — the same message can arrive twice even when the client
behaved perfectly. They guard different failure modes: flaky clients vs. queue
redelivery. The settle endpoint is additionally a no-op past PENDING, so even a
message that slips both layers cannot move money twice. On a downstream failure
the Lambda releases its dedup claim so the SQS retry reprocesses instead of
vanishing; velocity counters may over-count on retry, which only rejects sooner —
the safe direction for limits.

## 9. Lambda calls back into the API; it never touches Postgres

The Lambda approves or rejects, then calls `/internal/transfers/{id}/settle|fail`
authenticated by a shared service token. Lambda→JDBC was rejected: each concurrent
Lambda execution would hold its own connection (no pool sharing across sandboxes),
cold starts would pay for driver + pool init, and a second writer would duplicate
the locking discipline. Keeping Spring as the single database writer means the
pessimistic-locking settlement path has exactly one implementation. The Lambda is
plain Java (no Spring) for the same cold-start reason.
