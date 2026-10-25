# Controlled debugging tasks — symptoms first

Use a separate disposable worktree/database. Never check faulty revisions out over unsaved work or run them against shared services. The fault checkpoints are intentionally introduced implementation mistakes; neither is a production incident. The final implementation branch contains repaired behavior and additional regression tests.

Baseline: `02ac6a1`. Checkpoint hashes and full diagnoses are indexed separately in `solutions.md`; avoid that file until ready. To locate checkpoints without reading diagnoses: `git log --all --oneline --grep='refactor: compare payment retry identity fields'` and `git log --all --oneline --grep='refactor: update outbox publication bookkeeping'`.

## Task A — retried request has surprising acceptance

Invariant: one create key identifies one exact financial request, scoped to a merchant.

Simulated user report: “I retried creating a 30 CAD payment after changing it to 31 CAD, using the original key. The API accepted the request and returned the existing payment instead of reporting a conflict.” This report is simulated; the regression captures the actual local result.

Reproduce on the first checkpoint: register, create/fund two accounts, POST /api/v1/payments with a unique key and amount 30.00, then repeat with the same key and amount 31.00. Expected second status is 409, balances remain unchanged before capture, and only one payment exists. Use the external PostgreSQL test command from `verification.md` or Docker.

Investigation prompts: distinguish server-state replay from a new operation; inspect response ID/amount and persisted request fields. Reduce to two calls before inspecting implementation. Write a test before repair. Hints available on request; no diagnosis is included here.

## Task B — failed publication disappears from retry work

Invariant: a failed transport send must remain eligible for retry until the bounded attempt limit, then enter dead letter.

Simulated operator report: “The outbox failure counter increased, but a failed event no longer appeared in the pending queue after one transport failure.”

Reproduce on the second checkpoint: construct an outbox event and dispatcher with a transport that throws, call dispatch once, inspect stored status/attempts/nextAttemptAt. Expected PENDING, one attempt, future retry time; the failed event must not look successfully published. Unit-test fixtures suffice; do not stop real services to reproduce it. Then verify a successful send and exhaustion at the retry limit as related cases.

Investigation prompts: trace the event object's state before and after every transport call; inspect what the next due-query filters by. Do not assume a failure counter guarantees recoverability.

## Study workflow

1. Predict and record expected/actual observations in your own notes.
2. Reproduce with the smallest fixture.
3. Form hypotheses, inspect evidence and write a failing regression.
4. Repair the underlying invariant, test related behavior.
5. Only then read `solutions.md` and compare reasoning.

Repository investigations/fixes proceed independently of study progress. No personal investigation is attributed to you without your own notes.
