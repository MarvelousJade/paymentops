# Focused PaymentOps release

Reviewed: 2026-10-25. Starting committed source: `9316c46`; the pending PaymentOps expansion is the authorized starting implementation, not newly authored work in this release.

## Purpose

PaymentOps helps developers and payment-operations staff inspect how a synthetic payment becomes financial entries and external settlement evidence, and how retries and mismatches are handled. Without a linked timeline, payment state, ledger effects, and external records are difficult to explain together. This release provides one traceable workflow rather than another infrastructure showcase.

Suggested motivation to personalize: “I wanted to understand how payment systems keep financial effects consistent when clients retry and settlement arrives separately.” This is suggested wording, not a statement about personal experience.

## Scope and acceptance

Required: authorize → capture → balanced posting → simulated settlement → reconciliation → partial/full refund. Preserve existing ledger APIs. Use a Spring modular monolith and PostgreSQL; local outbox transport is the default. Kafka and observability services are optional demonstrations, not prerequisites.

Acceptance conditions:
- A funded synthetic source can pay a second account and refund back to its original balance.
- Each posting has one equal debit and credit; total money is conserved.
- Same-key create/refund retries resolve to the original operation; changed payloads are rejected.
- Duplicate and concurrent event delivery cannot repeat an effect; failure rolls back both claim and effect so retry remains possible.
- Invalid transitions, unauthorized access, insufficient funds and over-refunds leave money unchanged.
- Reconciliation detects missing/duplicate/mismatched settlement and retains its evidence.
- A reproducible HTTP demonstration checks the delivered application, not just mocked services.
- Verification records distinguish local PostgreSQL from Docker/Kafka checks and historical measurements.

Follow-ups: real broker crash/offset drills, multi-instance dispatch leases, paginated reconciliation, production identity and webhook management. Outside scope: real money/cards, bank connectivity, multi-currency, microservice extraction, cloud deployment or publication.

## Checklist and increments

1. [x] Inspect repository/history and preserve pending work; create `shaoyu/rework`.
2. [x] Review original references and define release acceptance before behavior changes.
3. [x] Establish database-backed baseline, review and commit existing application as a coherent checkpoint.
4. [x] Reproduce and repair meaningful retry/state defects with regression tests.
5. [x] Make infrastructure optional; add runnable demo and raw measurement evidence.
6. [x] Isolate two realistic fault investigations; preserve checkpoints, tests and separate repairs; integrate only corrected state.
7. [x] Run final checks, inspect changes, document interview/resume evidence and limitations.

Measurements: absolute end-to-end local workflow times, repeated identical workloads, status/balance/posting correctness. No valid payment-performance baseline currently exists, so no speedup percentage will be claimed. Test counts establish verification scope, not user impact.
