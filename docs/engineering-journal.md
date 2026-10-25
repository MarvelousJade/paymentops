# Engineering journal

## 2026-10-25 — baseline and environment hurdle

- Starting commit: `9316c46`. The authorized working tree already contained a large PaymentOps implementation, infrastructure and docs. Preserve that provenance rather than manufacture earlier increments. `.claude/skills/verify/SKILL.md` is unrelated and stays uncommitted; existing staged `AGENTS.md` is preserved.
- Problem/constraint: neither Java nor Docker was on PATH; Windows command discovery found neither installation and WSL reported no distributions. This is an environment constraint, not an application defect.
- Approach: downloaded portable Temurin JDK 21 and PostgreSQL 16.4 binary artifact outside the repository. Avoid installing Docker/WSL or changing system configuration. Testcontainers remains the default; an explicit external disposable DB option will permit genuine SQL/transaction testing here.
- Baseline observation: unchanged pending application compiled; `./mvnw -B -Dtest=MoneyTest,AuthServiceTest,RateLimiterTest test` passed 9 tests, 0 failed/errors/skipped (Maven 1:38 including downloads). This does not verify the payment workflow. Historical `target` reports were not treated as current results.
- Tradeoff: native PostgreSQL verifies real database semantics but not container startup, Kafka or Compose. Portable tool files/data remain outside Git. Lesson: record environment limits and seek a safe equivalent test layer instead of silently skipping integration tests.
- Setup mistake: the portable PostgreSQL distribution lacks `createdb`; the shell sequence continued and the first DB run had 30 context errors out of 39 tests because `paymentops_test` did not exist. Inspection of the first `FATAL` distinguished database setup from application failure. Created the disposable databases through JDBC and reran: 39 passed, 0 failures/errors/skips in 56.982 s. No application repair or regression test was appropriate for this setup mistake; fail-fast setup commands prevent it.
- Personal investigation notes: not supplied. Statements here describe repository work/evidence, not claims about what the owner personally investigated.

## BUG-001 — concurrent consumer claim poisons its transaction

- Origin/affected source: existing pending implementation, preserved in `011bfe6`; naturally identified by source inspection, not an injected defect.
- Problem: duplicate event delivery should be a successful no-op; the losing concurrent consumer instead failed at commit with `UnexpectedRollbackException`. Financial duplication was not observed; retry churn is the demonstrated impact.
- Reproduction: `EventIdempotencyTest.concurrentDeliveryCommitsOneEffectAndReturnsDuplicateWithoutTransactionFailure`. Hold the first transaction inside its effect; start the second delivery; query `pg_stat_activity` to confirm the second is blocked on `processed_events`; release the first. Both calls must return, one true/one false, and only one outgoing effect must exist.
- Hypotheses/evidence: sequential replay alone could not exercise the race. The first regression run produced 1 error in 2 tests, explicitly rollback-only at commit. The database wait confirmed contention on the claim, not an unrelated account lock. No deadlock was observed.
- Root cause: check-then-insert raced; catching `DataIntegrityViolationException` did not repair the PostgreSQL transaction or Spring rollback-only marker. JPA save/merge is not a conflict-safe claim primitive.
- Fix: one native `INSERT ... ON CONFLICT (consumer_name,event_id) DO NOTHING`; inspect affected-row count. It waits for competing claims without raising a uniqueness error, and remains in the same transaction as the effect.
- Alternatives/tradeoffs: a separate committed claim could lose work when effects fail; pessimistic locks cannot lock an absent row. PostgreSQL-specific SQL is accepted because PostgreSQL is already required. Transaction rollback still releases a failed claim.
- Verification: targeted run before fix: 2 tests, 1 error. After fix: EventIdempotencyTest + PaymentOpsFlowTest, 6 passed, 0 failures/errors/skips, 32.218 s. Rollback test proves both outgoing effect and claim disappear on failure, then retry succeeds once. Summaries: `docs/evidence/bug-001-*`.
- Prevention/design implication: model durable deduplication as an atomic database claim, not a caught constraint violation inside a live transaction.
- Interview explanation: concurrent replay exposed a transaction-boundary bug missed by sequential tests; a measured PostgreSQL lock wait made the reproduction controlled; conflict-safe insertion preserved atomic claim/effect rollback without sacrificing harmless duplicate responses. This is local integration evidence, not a production incident.
- Fix commit: the commit containing this entry (`git log --all --grep='claim duplicate events without aborting'`).
