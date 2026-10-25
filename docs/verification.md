# Verification record

## Environment and commands

2026-10-25: Windows 11 Pro 10.0.26300, portable Temurin 21.0.12.1+1, Maven Wrapper (Maven 3.9.16), PostgreSQL 16.4 on loopback port 15432. Local outbox transport; simulated settlement and webhooks. No Docker installation or WSL distribution. Tool archives/data live outside the repository. Native PostgreSQL archive: `io.zonky.test.postgres:embedded-postgres-binaries-windows-amd64:16.4.0`; JDK from Adoptium official binary API. Do not use this trust-authenticated disposable database for shared or real data.

Default CI/developer command: `./mvnw verify` (requires Docker; includes legacy LocalStack integration).

Equivalent PostgreSQL integration layer without Docker, against an **empty disposable test database**, never application/user data:

```bash
./mvnw -B '-Dtest=*,!AsyncSettlementFlowTest' \
  -Dpaymentops.test.external-db=true \
  -Dspring.datasource.url=jdbc:postgresql://127.0.0.1:15432/paymentops_test \
  -Dspring.datasource.username=ledger -Dspring.datasource.password= verify
```

`paymentops.test.external-db` disables only the PostgreSQL Testcontainers bean. Credentials use standard Spring datasource properties. The excluded class requires LocalStack/Docker and is **not counted as passed or skipped**. KafkaConfigurationTest only verifies bean wiring; its listener/admin are disabled, so it does not verify a real broker.

## Runs

| Source | Command/run | Result | Maven elapsed |
|---|---|---|---|
| `9316c46` + initial pending implementation | MoneyTest, AuthServiceTest, RateLimiterTest | 9 passed, 0 failed/errors/skipped | 1:38 including dependency downloads |
| `bc8caf2` + pending implementation + external DB switch | first DB suite attempt | 39 executed, 30 errors, 0 failures/skips: database missing | 37.881 s |
| same source | corrected empty DB, external command above | 39 passed, 0 failed/errors/skipped | 56.982 s |

The failed setup run was not an application regression: this minimal PostgreSQL binary distribution contains no `createdb` utility. Created disposable databases through JDBC, reran the suite and confirmed Flyway migrations/JPA validation and real HTTP tests passed. See journal. Raw bounded summaries are in `docs/evidence/`; full live Maven/Surefire reports are generated under ignored `target/`.

## Remaining gaps

Docker/Compose image startup, actual Kafka redelivery/rebalance/offset recovery, Schema Registry interoperability, hosted workflows/security scanners, AWS/LocalStack and rendered visual inspection are not established by native PostgreSQL or unit tests. Historical README load measurements are not new release measurements. No deployment or publication is performed.
