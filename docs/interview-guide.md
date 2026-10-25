# PaymentOps interview guide

## One sentence / 30 seconds

PaymentOps traces synthetic payments through authorization, capture, balanced ledger posting, simulated settlement, reconciliation and refunds. It is a Spring Boot modular monolith with PostgreSQL and a transactional outbox, so the interesting work is preserving financial invariants and evidence when requests retry or independent events arrive in a different order. Local transport keeps the demonstration small; Kafka is optional and not part of the current runtime guarantee.

## 60-second introduction

“Payment operations need more than a success flag: clients retry, settlement arrives separately, and refunds must not duplicate financial effects. This project links a payment timeline to immutable double-entry postings and external settlement evidence. API changes and outgoing events share a PostgreSQL transaction; consumers claim events with conflict-safe insertion in the same transaction as their effect. The delivered workflow runs locally without Kafka. Verification includes 48 native-PostgreSQL tests, a concurrent claim regression, and real HTTP payment/refund runs. Five warm local synthetic runs had a 1.205-second median workflow time with balances restored after refund. That is not throughput or production impact.”

Use this as project description, not a claim you personally implemented all existing code. Suggested motivation to personalize: “I wanted to understand what happens between accepting a payment request and proving that money moved only once.”

## Two-minute technical walkthrough / three-minute expanded version

```text
HTTP + JWT → PaymentController → PaymentService → PostgreSQL
                                           └→ outbox
scheduler → local EventRouter (or optional Kafka)
          → risk → ledger → settlement → reconciliation
                         └→ refund reverse postings
             each consumer: atomic claim + effect + next event
```

Trace one operation: POST create checks merchant ownership and payload-bound idempotency; state/timeline/outbox commit together. Scheduler routes risk and authorizes, without reserving funds. Capture locks payment and both accounts in ascending ID order, checks funds, appends equal debit/credit and a unique operation record. The next event creates a synthetic settlement record; reconciliation compares it to capture. Refund reverses account directions, retains historical posting evidence and advances cumulative refunded amount. Replay is handled by event claim plus business operation constraints.

For three minutes, add why claims roll back with effects and why settlement evidence must survive an early refund without overwriting refund progress.

## Code to read in order

1. `payment/PaymentController.java`, `PaymentDtos.java`: HTTP inputs, validation/scopes, decimal-to-cents boundary.
2. `payment/PaymentService.java`: create transaction, ownership, request identity, capture/refund request transitions.
3. `event/ProcessedEventRepository.java`, `IdempotentEventProcessor.java`: atomic `ON CONFLICT` claim and shared transaction.
4. `payment/LedgerPostingService.java`: ordered account locks, invariant checks, posting/effect transaction.
5. `event/OutboxDispatcher.java`, `EventRouter.java`: send/mark ordering, retry policy and local routing.
6. `settlement/SettlementSimulatorService.java`, `reconciliation/ReconciliationService.java`: independent evidence, mismatches/resolution.
7. `db/migration/V4__paymentops.sql` and tests `EventIdempotencyTest`, `PaymentOpsFlowTest`, `OutboxDispatcherTest`.

Paths above are under `src/main/java/dev/ledger` (SQL under `src/main/resources` and tests under `src/test/java/dev/ledger`).

## Four evidence-based discussion stories

| Story | Problem → hypothesis/evidence → decision/fix → tradeoff → verification → lesson |
|---|---|
| Concurrent deduplication (BUG-001, real repository defect) | Losing duplicate threw rollback-only at commit → lock-observed two-transaction reproduction identified uniqueness exception inside live transaction → `INSERT ON CONFLICT DO NOTHING` → PostgreSQL-specific SQL, but claim/effect remain atomic → failed 1/2 before repair; combined 6/6 after, rollback retry test → catching a database exception does not heal its transaction. Commit `ab86d88`. |
| Early refund vs settlement (BUG-002, real repository defect) | Partial refund suppressed settlement record → reorder valid handlers and inspect status/records; CAPTURED-only guard was the cause → retain independent settlement evidence without overwriting refund state; reconcile actual capture postings → single status no longer conveys every independent fact → expected one settlement/actual zero before, combined 7/7 after → cross-topic ordering must not determine whether evidence survives. Commit `ccff3e4`. |
| Optional infrastructure (engineering decision) | Full stack obscured the workflow → existing local transport already used identical handlers → db+app default, platform profile optional → easier run/debug but no evidence about broker offsets → packaged jar demo + both Compose configs parsed → choose the smallest complete workflow, not maximal infrastructure. Commit `02ac6a1`. |
| Test scheduling/retained state (BUG-004/005, real test defects) | Repeated DB run collided on token hash; configuration test sent pending events → constraint evidence showed fixed fixture key; spy showed first scheduled poll despite long delay → unique fixture token and explicit initial-delay test policy → deterministic test dispatch, actual scheduler separately verified by HTTP demo → auth repeated twice; final 48 passed → fixtures and asynchronous startup need explicit ownership. Commits `e772520`, `7e14b85`. |

Detailed hypotheses, observations, provenance and unfavorable results: [journal](engineering-journal.md). Controlled tasks are separate: [symptoms first](debugging/README.md), then solutions only when ready. They are intentionally injected faults, not the above real findings. Personal investigation notes have not been supplied; don't say “I discovered…” based solely on this document.

## Five-minute demonstration and backup

Follow [walkthrough](walkthrough.md). Start local app, run `node scripts/demo.mjs` with provisioned operator credentials, show final REFUNDED/100+0, then use printed correlation ID in the console. Inspect capture/refund timeline and run reconciliation. Optional synthetic wrong-amount import demonstrates classified exceptions after timing runs. Backup: saved demo JSON + database-check files and targeted tests; state explicitly that these are previous local runs.

## Questions and answer outlines

- **Why a modular monolith/PostgreSQL?** Account, claim, posting and event writes need one ACID boundary. Microservices would add distributed coordination without a demonstrated requirement. Follow-up: what happens when only one module needs independent scaling?
- **Why Java/Spring?** Existing stack offers transaction proxies, validation, JWT authorization, JPA, migrations and HTTP integration testing. Follow-up: why does invoking a method through a proxy matter for `@Transactional`?
- **Why integer cents?** Exact CAD minor units; API decimal strings convert with explicit validation. Follow-up: what are long overflow and multi-currency limitations? Supported risk limits bound the demonstrated payments; production arithmetic needs comprehensive checked-overflow policy.
- **Exactly once?** No transport exactly-once claim. Outbox may resend; durable per-consumer claims and posting constraints make tested effects replay-safe. Follow-up: why must claim and effect roll back together?
- **Why conflict-safe SQL instead of catch?** PostgreSQL uniqueness errors abort transactions; a caught exception still produced rollback-only. Follow-up: what if the first claimant rolls back while the second waits?
- **Why ordered pessimistic locks?** Opposing transfers lock the same global account order; short critical sections avoid retry storms under hot-account contention. Follow-up: could another operation break that lock order?
- **Is authorization a hold?** No; risk approval only. Concurrent approved payments can later fail capture for insufficient funds. Follow-up: how would reservations and expiry change the schema?
- **Are refunds reserved when requested?** No; pending refunds are validated again during posting and may be asynchronously rejected. Follow-up: how would you reserve pending refund totals to improve admission feedback?
- **What do tests establish?** Real SQL/transactions/HTTP plus mock transport state tests; native PostgreSQL is not Docker/Kafka recovery. Follow-up: how would you inject failure between commit and offset acknowledgement?
- **Why can a refunded payment settle later?** Settlement describes captured external movement, refund describes return movement; both need evidence. Follow-up: why is one status field insufficient under more complex requirements?
- **What remains limited?** Single dispatcher instance, no lease protocol, unpaginated reconciliation, simulated provider/webhooks, no Registry enforcement, no production identity or audited webhook requeue. Follow-up: which improvement would real usage justify first?

## Measurement questions

Baseline: none for this payment workflow; report absolute result only. Five sequential equivalent synthetic datasets after one warm-up, warm JVM/native PostgreSQL on one Windows host, local transport, 250 ms outbox/50 ms HTTP polling. Median 1205.397 ms, range 1186.547–1459.896; no percentile, speedup or customer claim. Each run must pass assertions; direct SQL checks verify 3 equal posting pairs, one settlement and full balance restoration, including after JVM restart. Timing excludes setup and final evidence reads; raw request samples/metadata/hash are retained in [impact evidence](impact-and-resume-evidence.md). It leaves throughput, real providers, distributed contention and tail latency unproven.

## Quick preparation sequence

1. Understand purpose and diagram; explain who needs linked operational evidence.
2. Run main demonstration; inspect one payment and correlation ID.
3. Read the code map; trace transaction boundaries yourself.
4. Reproduce real regressions in a disposable worktree; try controlled tasks without reading solutions.
5. Review measurement files, decisions and limitations; write personal investigation notes separately.
6. Practice the introduction and follow-up questions without notes.

## One-page talking points

- Useful scope: synthetic authorize/capture/settle/reconcile/refund; no real money or reserved authorization.
- Design: one deployable/PostgreSQL, package-by-feature, transactional outbox, local transport first.
- Invariants: conserved money, equal posting sides, no overdraft, bounded refunds, payload-bound retries, atomic claims/effects.
- Strongest debugging: PostgreSQL rollback-only race and early-refund settlement ordering.
- Evidence: 48 PostgreSQL tests; separate 9 Lambda tests; five local HTTP runs, 1.205 s median; direct SQL before/after restart; browser interactions and initial rendered console inspected.
- Boundaries: not production, not Kafka recovery verification, no performance improvement percentage or adoption claim.
- Next improvements: real broker recovery tests, dispatcher leases, pagination, reservation semantics and production security requirements only when justified.
- Ownership: distinguish pre-existing implementation, repository changes and what you personally studied/performed; use factual project claims until personal contribution is confirmed.
