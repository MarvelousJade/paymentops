# PaymentOps

PaymentOps is a Java/Spring Boot application for tracing a **synthetic payment** from authorization through ledger posting, simulated settlement, reconciliation, and refunds. It helps developers and operations reviewers inspect financial consistency and retry behavior without connecting to a payment provider.

```text
Authorize → capture → equal debit/credit → simulated settlement
          → reconciliation → partial/full refund
```

The application is a modular monolith with PostgreSQL, a transactional outbox, scoped JWTs, immutable timelines and an optional operations console. Local event delivery is the default; Kafka and dashboards are optional. Authorization evaluates risk rules; it does **not reserve funds**. Capture can still fail for insufficient funds.

Use disposable synthetic data only. No real cards, bank credentials or customer information. This is not a payment processor and has not been validated for production use.

## Quick start

Prerequisites: Docker Engine with Compose, or Java 21 and PostgreSQL 16. Node 22+ runs the optional automated demonstration.

```bash
cp .env.example .env
openssl rand -base64 48  # put this in JWT_SECRET in .env
openssl rand -hex 32    # put this in WEBHOOK_SIGNING_SECRET in .env
docker compose up --build
```

The default Compose configuration starts **only PostgreSQL and the application**. Open:
- API explorer: http://localhost:8080/swagger-ui.html
- Operations console: http://localhost:8080/operations.html
- Health: http://localhost:8080/actuator/health
- Local metrics: http://localhost:8080/actuator/prometheus

To run Java directly, start PostgreSQL (`docker compose up -d db` or your own disposable instance), export `JWT_SECRET`, `DB_URL`, `DB_USER`, `DB_PASSWORD` and `WEBHOOK_SIGNING_SECRET`, then run `./mvnw spring-boot:run` (`.\mvnw.cmd spring-boot:run` in PowerShell). Maven does **not** load `.env` automatically. Default JDBC URL is `jdbc:postgresql://localhost:15432/ledger`.

## Demonstrate the complete workflow

Follow [the five-minute walkthrough](docs/walkthrough.md) to provision a local operator and run:

```bash
OPERATOR_EMAIL=demo-operator@example.test OPERATOR_PASSWORD="$DEMO_OPERATOR_PASSWORD" node scripts/demo.mjs
```

The script creates fresh synthetic users/accounts, funds 100 CAD, pays 30 CAD, checks safe retries and payload conflicts, verifies settlement/reconciliation, refunds 10 then 20 CAD, and verifies final balances of 100/0. It prints IDs, assertion-backed results and timings, never access tokens. It refuses non-loopback targets.

In Swagger, register/login, authorize with the access token, create/fund accounts, then use:

```text
POST /api/v1/payments                         (Idempotency-Key required)
GET  /api/v1/payments/{id}                     (poll authorization)
POST /api/v1/payments/{id}/capture             (Idempotency-Key required)
GET  /api/v1/payments/{id}/timeline
POST /api/v1/payments/{id}/refunds             (Idempotency-Key + amount)
GET  /api/v1/payments/{id}/deliveries
```

Merchants can only inspect their own payments. Operations APIs require an operator/auditor role. Registration always creates a merchant; elevated roles require controlled database administration and a fresh login. Capture/cancel are state-idempotent operations; create/refund also bind keys to payloads. Responses show current state, not a cached first-response body.

## Verify

```bash
./mvnw verify
./mvnw -f settlement-lambda/pom.xml verify
docker compose config --quiet
docker compose --profile platform config --quiet
```

Integration tests use real PostgreSQL through Testcontainers. Legacy async-transfer tests also require LocalStack. For a Docker-free disposable PostgreSQL option, exact commands and results are in [verification](docs/verification.md). Do not run integration tests against real/shared data.

The release verification includes a reproduced/repaired concurrent deduplication transaction failure and an early-refund/late-settlement ordering bug. [Journal](docs/engineering-journal.md) records failing and passing evidence, origin and tradeoffs. [Impact evidence](docs/impact-and-resume-evidence.md) separates local measurements, historical results and unsupported claims.

## Optional infrastructure

```bash
# Set PAYMENTOPS_MESSAGING_MODE=kafka and OTEL_TRACING_ENABLED=true in .env first.
docker compose --profile platform up --build
```

This adds Kafka, Schema Registry, OpenTelemetry Collector, Prometheus, Grafana, Tempo, Loki and supporting exporters. Start/order availability is handled by outbox retries; default local mode does not need these services. Schema registration is illustrative: the producer currently sends JSON strings and does not enforce Registry schemas. Actual broker recovery and dashboard rendering remain unverified on the current host.

GitHub workflows define CI and security checks. Staging publication is **manual** and requires configured protected environments/secrets; no push, deployment or publication is part of this release.

## Read next

1. [Purpose, scope and acceptance](docs/release-plan.md)
2. [Architecture and code map](docs/ARCHITECTURE.md)
3. [References and comparison](docs/references.md)
4. [Walkthrough](docs/walkthrough.md)
5. [Engineering journal](docs/engineering-journal.md)
6. [Interview guide](docs/interview-guide.md)
7. [Impact and resume evidence](docs/impact-and-resume-evidence.md)
8. [Verification and limitations](docs/verification.md), [changelog](docs/CHANGELOG.md)

Additional references: [events](docs/EVENT_CATALOG.md), [runbooks](docs/RUNBOOKS.md), [test plan](docs/TEST_PLAN.md), [security](docs/SECURITY.md), [release procedure](docs/RELEASE.md), [historical ledger performance](docs/PERFORMANCE.md), [original decisions](DECISIONS.md).
