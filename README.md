# ledger-service

A payments/ledger REST API built the way banks build them: double-entry
bookkeeping over an append-only ledger, idempotent money transfers under
pessimistic locking, async settlement through SQS → Lambda with DynamoDB
dedup, and Spring Batch statements/reconciliation.

The interesting problem: **move money concurrently without losing a cent** —
no lost updates, no overdrafts, no deadlocks, and every retry (client or
queue redelivery) lands exactly once.

## Architecture

```
Client (Swagger / curl)
        |  POST /transfers  (Idempotency-Key header)
        v
Spring Boot API (Render) <-------- PostgreSQL (ledger = source of truth)
        |                                ^
        |  202 Accepted + publish        |  internal settlement endpoint
        v                                |  (service-token auth)
       SQS ------> Lambda (Java 21) -----+
        |             |  limit & velocity checks,
        v             |  message dedup via DynamoDB conditional put
       DLQ (CloudWatch alarm on depth > 0)

@Scheduled / Spring Batch: monthly statements (CSV), nightly ledger reconciliation
```

## Stack

Java 21 · Spring Boot 4 · PostgreSQL 16 · Flyway · Spring Security (JWT resource
server) · Spring Batch · Spring Cloud AWS · AWS SAM (SQS, Lambda, DynamoDB,
CloudWatch) · Bucket4j · Testcontainers + LocalStack · k6 · Docker · GitHub Actions

## Quickstart

```bash
cp .env.example .env          # fill in JWT_SECRET (openssl rand -base64 48)
docker compose up -d db
./mvnw spring-boot:run
```

Swagger UI: http://localhost:8080/swagger-ui.html · Health: /actuator/health

Runs fully local by default (`SETTLEMENT_MODE=sync`) — no AWS account needed.

## What's inside

- **Auth** — register/login/refresh/me. BCrypt, 15-minute HMAC JWTs, 7-day
  rotating refresh tokens stored hashed; reuse of a rotated token revokes the
  whole family.
- **Accounts & ledger** — money is BIGINT cents (never `double`), the
  append-only `ledger_entries` table is the source of truth, `balance_cents`
  is a materialized convenience checked nightly. Status changes audited
  (who/when).
- **Transfers** — `Idempotency-Key` replay returns the original result (the
  Stripe pattern). Settlement locks both accounts `FOR UPDATE` in ascending-id
  order (the deadlock-avoidance trick), checks overdraft inside the critical
  section, writes debit + credit + two ledger rows in one transaction.
  Business failures persist as `FAILED` transfers so replays see the same
  outcome. Bucket4j rate limiting per customer.
- **Async settlement (AWS)** — in async mode `POST /transfers` returns
  `202 Accepted` and publishes to SQS. A plain-Java 21 Lambda enforces
  max-single / daily-total / per-minute limits with atomic DynamoDB counters,
  dedups at-least-once deliveries with a conditional put (claim released on
  downstream failure so retries reprocess), and calls back into the API's
  service-token-authenticated internal endpoints — the API stays the single
  database writer. Poison messages redrive to a DLQ after 3 attempts;
  a CloudWatch alarm fires on DLQ depth > 0. All verified live.
- **Batch** — monthly CSV statements (opening/closing balances derived from
  ledger sums) via Spring Batch, generated on demand or on schedule; nightly
  reconciliation recomputes every account's ledger sum vs. its materialized
  balance and bumps a drift metric.

## The test that matters

`MoneyConservationTest`: 60 parallel transfers among 3 accounts from a
16-thread pool, mixed directions. Asserts total money is conserved, no balance
goes negative, the ledger agrees with every materialized balance, and nothing
deadlocks. A second test fires 12 threads at the same idempotency key and
asserts exactly one transfer exists afterward.

45 tests total (unit + Testcontainers integration + LocalStack async seam +
Lambda rules/handler). Line coverage: 85% overall, 89% service layer (JaCoCo).

## Load (k6, local)

100 VUs, 90 s, 1:2 transfers:reads, 10 accounts, Dockerized Postgres on the
same laptop as the load generator:

| | p50 | p95 |
|---|---|---|
| `GET /accounts/{id}/transactions` | 88 ms | 253 ms |
| `POST /transfers` (sync settle) | 312 ms | 978 ms |

~276 req/s sustained, 0% errors, 25k requests. Transfer p95 is dominated by
row-lock queueing on hot accounts — the deliberate cost of pessimistic
locking; reads stay fast because MVCC never blocks on writers.

```bash
k6 run k6/smoke.js -e BASE=http://localhost:8080
```

## AWS deployment

First time: `aws configure`, then the $5 budget alarm (command below), then:

```bash
./mvnw -f settlement-lambda/pom.xml package        # shaded Lambda jar
sam deploy --guided -t infra/template.yaml         # prompts for ApiBaseUrl + ServiceToken
```

```bash
aws budgets create-budget --account-id $(aws sts get-caller-identity --query Account --output text) \
  --budget '{"BudgetName":"ledger-cap","BudgetLimit":{"Amount":"5","Unit":"USD"},"TimeUnit":"MONTHLY","BudgetType":"COST"}' \
  --notifications-with-subscribers '[{"Notification":{"NotificationType":"ACTUAL","ComparisonOperator":"GREATER_THAN","Threshold":80},"Subscribers":[{"SubscriptionType":"EMAIL","Address":"YOUR_EMAIL"}]}]'
```

API host env for async mode: `SETTLEMENT_MODE=async`,
`SETTLEMENT_QUEUE=<QueueUrl output>`, `SERVICE_TOKEN=<same as deploy>`, AWS
credentials, and `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` for JSON logs.
`render.yaml` provisions the API + free Postgres on Render as a blueprint.

## Key decisions

One paragraph per non-obvious choice in [DECISIONS.md](DECISIONS.md) —
modular monolith, BIGINT money, lock ordering vs. optimistic retries, the
claim-then-settle idempotency pattern, two dedup layers, why the Lambda never
touches Postgres.

## Deliberately out of scope

Microservices, Kubernetes, Kafka, multi-currency, interest accrual, PDF
statements, OAuth social login. Each has a DECISIONS-style answer for "why
not" — ask.
