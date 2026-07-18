# ledger-service

A payments/ledger REST API modeled on real bank patterns: accounts, double-entry
bookkeeping, idempotent money transfers, async settlement, and batch statements.

## Architecture

```
Client (Swagger / curl)
        |  POST /transfers  (Idempotency-Key header)
        v
Spring Boot API  <-------- PostgreSQL (ledger = source of truth)
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

Java 21 · Spring Boot 4 · PostgreSQL 16 · Flyway · Spring Security (JWT) ·
Spring Batch · Testcontainers · springdoc-openapi · Docker Compose · GitHub Actions

## Quickstart

```bash
cp .env.example .env          # fill in JWT_SECRET (openssl rand -base64 48)
docker compose up -d db
./mvnw spring-boot:run
```

Swagger UI: http://localhost:8080/swagger-ui.html
Health: http://localhost:8080/actuator/health

## Async settlement (AWS)

Local dev runs `SETTLEMENT_MODE=sync` (no AWS needed). In async mode
`POST /transfers` returns `202 Accepted` and publishes to SQS; a Java 21 Lambda
runs limit/velocity checks (DynamoDB dedup + counters) and calls back into
`/internal/transfers/{id}/settle|fail` with a service token. Poison messages hit
the DLQ after 3 attempts; a CloudWatch alarm fires on DLQ depth > 0.

First time only — credentials and the $5 budget alarm:

```bash
aws configure                        # paste access key, secret, region (e.g. us-east-1)
aws budgets create-budget --account-id $(aws sts get-caller-identity --query Account --output text) \
  --budget '{"BudgetName":"ledger-cap","BudgetLimit":{"Amount":"5","Unit":"USD"},"TimeUnit":"MONTHLY","BudgetType":"COST"}' \
  --notifications-with-subscribers '[{"Notification":{"NotificationType":"ACTUAL","ComparisonOperator":"GREATER_THAN","Threshold":80},"Subscribers":[{"SubscriptionType":"EMAIL","Address":"YOUR_EMAIL"}]}]'
```

Deploy the AWS slice:

```bash
sam build --use-container -t infra/template.yaml   # container build; no local Maven needed
sam deploy --guided -t infra/template.yaml         # prompts for ApiBaseUrl + ServiceToken
```

Then set on the API host: `SETTLEMENT_MODE=async`, `SETTLEMENT_QUEUE=<QueueUrl output>`,
`SERVICE_TOKEN=<same value>`, plus AWS credentials for SQS publish.

## Key decisions

See [DECISIONS.md](DECISIONS.md).
