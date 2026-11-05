# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

The primary user is the project owner preparing and demonstrating an interview portfolio project. Interviewers and developers review the workflow, implementation, and evidence. The operations console also supports a simulated payment operator investigating a payment or reconciliation exception; auditors have read-only access through scoped APIs.

These priorities and the existing repository boundaries were approved through the user's acceptance of the proposed defaults.

## Product Purpose

PaymentOps traces a synthetic payment from authorization through capture, balanced ledger posting, simulated settlement, reconciliation, and partial or full refund. It makes financial consistency, safe retries, and event ordering understandable without connecting to a payment provider.

Success means running one complete, reproducible workflow, inspecting its evidence, and explaining the implementation and tradeoffs. Exception investigation and recovery support that workflow rather than expanding it into a production operations platform.

The project demonstrates transaction boundaries, concurrency control, idempotency, authorization, integration testing, and evidence-based debugging. Suggested motivation to personalize: “I wanted to understand how payment systems keep financial effects consistent when clients retry and settlement arrives separately.” This is not a claim about the owner's personal experience.

## Positioning

An educational, local-first payment workflow with linked payment timelines, balanced financial entries, and separate settlement evidence. It is not a payment processor. Its value is the inspectable implementation and reproducible correctness checks, not a claim of unique commercial capability.

## Operating Context

- Run locally with Docker Compose (application and PostgreSQL), or Java 21 and disposable PostgreSQL 16. Node 22+ runs the optional demonstration scripts.
- Use `scripts/demo.mjs` and `docs/walkthrough.md` for the synthetic end-to-end demonstration. The script refuses non-loopback targets.
- Inspect APIs through `/swagger-ui.html` and operations through `/operations.html`, served by the same Spring Boot application at port 8080 by default.
- Console users supply an operator or auditor JWT; tokens are not persisted by the console. Public registration creates merchants only; elevated access requires controlled provisioning and a fresh login.
- Study `docs/interview-guide.md` first, then architecture, implementation files, the engineering journal, and symptoms-first debugging exercises. Preserve the distinction between repository evidence and personal investigation.

## Capabilities and Constraints

- Existing stack: Java/Spring Boot modular monolith, PostgreSQL, database migrations, scoped JWTs, and a transactional outbox. Local event transport is the default; Kafka and observability infrastructure are optional.
- Main workflow: authorize, capture, equal debit/credit posting, simulated settlement, reconciliation, partial/full refund. Preserve existing ledger APIs.
- Console capabilities: health inspection, payment timeline lookup by correlation ID, reconciliation exception filtering, reconciliation execution, and approved dead-letter replay. API permissions govern available operations; auditors cannot perform writes.
- Financial invariants: conserve total money, retain balanced posting pairs, prevent duplicate financial effects, reject invalid transitions and over-refunds, and retain evidence through retries and reordered events.
- Authorization evaluates risk; it does not reserve funds. Capture may fail for insufficient funds. Refund requests are validated again during asynchronous posting.
- Create/refund idempotency keys bind to payloads. Capture/cancel use aggregate state for replay. Responses show current state rather than a cached original response.
- Use disposable synthetic data only. No real cards, bank credentials, customer information, money movement, or bank connectivity.
- Keep one complete workflow understandable before adding infrastructure or features. Do not require Kafka for the default demonstration.
- Outside the current scope: multi-currency, microservice extraction, production identity, cloud deployment, and real payment-provider integration.
- Known limitations remain explicit: one intended dispatcher, no multi-instance lease protocol, unpaginated reconciliation, no risk-review approval UI, no webhook delivery-requeue API, and no enforced Schema Registry contracts. Real broker recovery and production readiness are not established.

## Brand Commitments

Preserve the name PaymentOps and factual engineering language. Do not invent customers, testimonials, adoption, production incidents, performance improvements, personal motivation, or personal contribution. Describe intentionally injected debugging defects as exercises, not defects that affected the main implementation.

## Evidence on Hand

- `README.md` and `docs/release-plan.md`: purpose, scope, startup, and acceptance conditions.
- `docs/ARCHITECTURE.md`: component boundaries, financial invariants, authorization, and limitations.
- `src/main/resources/static/operations.html`: existing operations console, not a new visual brief.
- `src/test/java/dev/ledger/`: regression and integration tests; `settlement-lambda/` contains a separately verified legacy component.
- `scripts/demo.mjs`, `scripts/console-smoke.mjs`, and `scripts/check-evidence.mjs`: reproducible demonstration and evidence checks.
- `docs/verification.md` and `docs/evidence/`: recorded local and hosted verification. Historical test counts and timings belong to their recorded source/environment, not a new guarantee.
- `docs/engineering-journal.md` and `docs/debugging/`: real defects and controlled exercises with distinct provenance.
- No production usage, customer impact, or current-session runtime verification is established by this product record.

## Product Principles

1. Complete and explain one useful workflow before expanding the system.
2. Treat database postings and retained evidence as authoritative, not a success flag or metric.
3. Make retries and event ordering explicit parts of correctness.
4. Keep local operation small and optional infrastructure genuinely optional.
5. Separate measured results, historical evidence, hypotheses, and personal experience.

## Open Decisions

No product-specific accessibility standard, additional audience needs, or production deployment requirements have been established. Their absence is not permission to remove existing accessibility or security behavior. Visual direction is not part of this initialization.
