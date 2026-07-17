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

## Key decisions

See [DECISIONS.md](DECISIONS.md).
