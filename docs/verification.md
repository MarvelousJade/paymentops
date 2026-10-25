# Verification record

## Environment and commands

2026-10-25: Windows 11 Pro 10.0.26300, portable Temurin 21.0.12.1+1, Maven Wrapper (Maven 3.9.16), PostgreSQL 16.4 on loopback port 15432. Local outbox transport; simulated settlement and webhooks. No Docker engine or WSL distribution. Standalone Docker 28.5.1 and Compose 2.39.4 clients were downloaded later for config validation only. Tool archives/data live outside the repository. Native PostgreSQL archive: `io.zonky.test.postgres:embedded-postgres-binaries-windows-amd64:16.4.0`; JDK from Adoptium official binary API. Do not use this trust-authenticated disposable database for shared or real data.

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
| `a249277` + expanded payment tests | broader retained-DB verify | 48 executed, 1 error: shared expired-token fixture | 1:26 |
| `e772520` + expanded payment tests | same verify after auth fixture repair | all 48 tests passed; build failed at jar rename (running JVM held target jar) | 1:21 |
| `e772520` + scheduler repair + expanded payment tests (committed as `7e14b85`/`c8a3b85`) | native PostgreSQL verify, demo JVM stopped | 48 passed, 0 failed/errors/skipped; BUILD SUCCESS, jar and JaCoCo generated | 55.952 s |
| production workflow at `ccff3e4` + local profile changes | settlement-lambda verify | 9 passed, 0 failed/errors/skipped | 32.290 s |

Targeted failure/repair runs are preserved individually in `docs/evidence/bug-*` and `ctrl-*`; see journal for exact provenance. Do not sum runs into a unique-test count. The final PostgreSQL suite contains 48 tests across 13 classes; the legacy Docker-dependent AsyncSettlementFlowTest is excluded, not reported as skipped. The separate Lambda run contains 9 tests.

The failed setup run was not an application regression: this minimal PostgreSQL binary distribution contains no `createdb` utility. Created disposable databases through JDBC, reran the suite and confirmed Flyway migrations/JPA validation and real HTTP tests passed. See journal. Raw bounded summaries are in `docs/evidence/`; full live Maven/Surefire reports are generated under ignored `target/`.

## Runtime and measurement verification

- Packaged jar launched with local transport/native PostgreSQL. Five measured HTTP workflow runs after one warm-up passed; median 1205.397 ms, range 1186.547–1459.896 ms. Source `c8a3b85`, exact jar hash/environment/raw samples in `docs/evidence/measurement.json` and `demo-1..5.json`. This is absolute warm synthetic workflow time, not throughput or an improvement percentage.
- `scripts/VerifyDemo.java` independently checked 3 equal, event-linked posting pairs, one settled record, fully refunded amounts, 100/0 CAD balances and no unresolved exceptions for each measured payment. Stopped and relaunched the same copied jar; repeated these assertions against saved data (`demo-after-restart.txt`). This verifies the tested JVM restart, not an arbitrary crash/offset drill.
- Programmatic HTTP checks retrieved health UP, operations HTML, OpenAPI JSON and Prometheus JVM/posting/backlog metrics. Saved raw demo and SQL output are exported evidence; no actual merchant export feature is claimed.
- Chrome 156 headless programmatic interaction: invalid-token 401, successful operator connection, existing-payment correlation search, repeated search, empty exceptions, reconciliation/refresh, missing-dead-letter 404 and preserved correlation input all passed. Script: `scripts/console-smoke.mjs`; artifact: `docs/evidence/console-interaction.json`.
- Rendered visual inspection: read the generated 1280×1100 Chrome screenshot of the initial console. Headings, inputs, buttons and empty states rendered without overlap at that viewport; page scrolls to remaining content. Screenshot lives in ignored `target/paymentops-console.png`. No human interaction, mobile, keyboard-accessibility or screen-reader evaluation claimed.
- Both default and `platform` Compose configurations validate with Compose 2.39.4 without an engine; default `config --services` returns db/app. Container image execution is not verified.

## Remaining gaps

Docker/Compose image startup, actual Kafka redelivery/rebalance/offset recovery, Schema Registry interoperability, hosted workflows/security scanners and AWS/LocalStack are not established by these checks. Historical ledger load measurements are not new release measurements. No deployment or publication is performed. Initial screenshot inspection is not a usability study. More limitations: CHANGELOG.md.
