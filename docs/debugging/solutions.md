# Diagnoses and evidence — read after investigating

Learner-facing symptoms: [README](README.md). Both faults were intentionally introduced on `shaoyu/learning` after verified baseline `02ac6a1`. Neither repaired behavior that was wrong on the implementation branch; useful integration is regression coverage and documented reasoning. These are not production incidents or claims about personal investigation.

## A — payload identity

- Fault checkpoint: `166bf59` (`refactor: compare payment retry identity fields`).
- Repair: `b393f0b` (`fix: reject changed amounts on payment creation retries`).
- Cause: removed amount from `PaymentService.requireSameRequest`; key lookup still returned the original payment but no longer rejected a financially different request.
- Test: `PaymentOpsFlowTest.sameCreateKeyRejectsChangedAmountWithoutCreatingFinancialEffects`.
- Evidence: `docs/evidence/ctrl-001-failing.txt` (409 expected, 202 observed); passing file and CTRL-001 journal entry.
- Principle: every operation-defining field belongs in the idempotency contract; a cached ID alone is not payload safety.

## B — premature publication

- Fault checkpoint: `3674349` (`refactor: update outbox publication bookkeeping`).
- Repair: `fix: preserve pending outbox state until transport succeeds` (find exact hash with `git log --all --grep='preserve pending outbox state'`).
- Cause: set PUBLISHED before sending. A transport failure updated attempts/backoff but left the status outside the due-query's PENDING filter.
- Tests: `OutboxDispatcherTest`, three cases: retry/success, exhaustion/replay and state at acknowledgement.
- Evidence: `docs/evidence/ctrl-002-failing.txt` and passing summary; CTRL-002 journal entry.
- Principle: send-then-mark allows duplicate delivery on a crash; mark-then-send can lose work. Choose at-least-once plus idempotent effects rather than false exactly-once claims.

## Commands and checkpoints

Use `git worktree add --detach ../paymentops-debug <checkpoint>` and a **new disposable database**. The faulty checkpoints predate their new regression tests: apply only the relevant test-file diff from its repair commit if you want to run the exact failing test. For A, copy the test from the repair without restoring service code; for B, copy `OutboxDispatcherTest.java` only. Do not cherry-pick the full repair and expect a failure.

Target B: `./mvnw -Dtest=OutboxDispatcherTest test` (no Docker).
Target A: `./mvnw -Dtest=PaymentOpsFlowTest test` (Docker) or add the external DB options from `verification.md`.

The corrected branch must pass broader tests before merge. No broken branches/tags are published.
