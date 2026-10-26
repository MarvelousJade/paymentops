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
| merged local main `d52b67c` | native PostgreSQL verify against a newly created `paymentops_release_test` database (same command, new database name) | 48 passed, 0 failed/errors/skipped; BUILD SUCCESS | 50.198 s |
| merged local main `d52b67c` | settlement-lambda verify | 9 passed, 0 failed/errors/skipped; BUILD SUCCESS | 5.783 s |

Post-integration summaries: `evidence/merged-postgres.txt` and `evidence/merged-lambda.txt`. `d52b67c` is the no-fast-forward integration of `shaoyu/rework` into local main. Later verification-record edits do not alter application code. Measured source `c8a3b85` has identical production source/pom to the integrated result.

Targeted failure/repair runs are preserved individually in `docs/evidence/bug-*` and `ctrl-*`; see journal for exact provenance. Do not sum runs into a unique-test count. At that initial release checkpoint, the PostgreSQL suite contained 48 tests across 13 classes; the legacy Docker-dependent AsyncSettlementFlowTest is excluded, not reported as skipped. The separate Lambda run contains 9 tests.

The failed setup run was not an application regression: this minimal PostgreSQL binary distribution contains no `createdb` utility. Created disposable databases through JDBC, reran the suite and confirmed Flyway migrations/JPA validation and real HTTP tests passed. See journal. Raw bounded summaries are in `docs/evidence/`; full live Maven/Surefire reports are generated under ignored `target/`.

## Runtime and measurement verification

- Packaged jar launched with local transport/native PostgreSQL. Five measured HTTP workflow runs after one warm-up passed; median 1205.397 ms, range 1186.547–1459.896 ms. Source `c8a3b85`, exact jar hash/environment/raw samples in `docs/evidence/measurement.json` and `demo-1..5.json`. This is absolute warm synthetic workflow time, not throughput or an improvement percentage.
- `scripts/VerifyDemo.java` independently checked 3 equal, event-linked posting pairs, one settled record, fully refunded amounts, 100/0 CAD balances and no unresolved exceptions for each measured payment. Stopped and relaunched the same copied jar; repeated these assertions against saved data (`demo-after-restart.txt`). This verifies the tested JVM restart, not an arbitrary crash/offset drill.
- Programmatic HTTP checks retrieved health UP, operations HTML, OpenAPI JSON and Prometheus JVM/posting/backlog metrics. Saved raw demo and SQL output are exported evidence; no actual merchant export feature is claimed.
- Chrome 156 headless programmatic interaction: invalid-token 401, successful operator connection, existing-payment correlation search, repeated search, empty exceptions, reconciliation/refresh, missing-dead-letter 404 and preserved correlation input all passed. Script: `scripts/console-smoke.mjs`; artifact: `docs/evidence/console-interaction.json`.
- Rendered visual inspection: read the generated 1280×1100 Chrome screenshot of the initial console. Headings, inputs, buttons and empty states rendered without overlap at that viewport; page scrolls to remaining content. Screenshot lives in ignored `target/paymentops-console.png`. No human interaction, mobile, keyboard-accessibility or screen-reader evaluation claimed.
- Both default and `platform` Compose configurations validate with Compose 2.39.4 without an engine; default `config --services` returns db/app. Container image execution is not verified.

## Subsequent hosted verification after authorized publication

At application commit `0ef3009`, [CI run 40246048398](https://github.com/MarvelousJade/paymentops/actions/runs/40246048398) succeeded on Ubuntu 24.04.5/Temurin 21.0.12+1: executable checks, JSON/Compose validation, all **50** application tests (including Testcontainers PostgreSQL and 2 LocalStack async tests), separate **9** Lambda tests, and Docker image build. Both suites had zero failures/errors/skips. Maven times: 1:38 and 9.309 s. This is one hosted run, not a sum of earlier local runs. Raw bounded summary: `evidence/hosted-ci-passing.txt`. The first publication run had failed before Maven due to missing executable bits; BUG-006 records reproduction and repair.

[Security run 40246048460](https://github.com/MarvelousJade/paymentops/actions/runs/40246048460): CodeQL succeeded; Trivy failed with vulnerability findings; Dependency-Check could not analyze because NVD returned 429 and no database was available; dependency review was skipped on push. This is **not** an all-green security result. See journal and `evidence/hosted-security-status.json` / `hosted-security-findings.txt` for exact source/provenance. Final publication-note changes did not alter application dependencies. This is historical failure evidence; the subsequent remediation below supersedes its unresolved status.

## Security remediation verification

Application update `83c8179` and scanner repair `cb0a2e7` preserve the payment workflow while patching Boot-managed libraries, lz4, Kotlin, Spring Retry, embedded Swagger/DOMPurify and the standalone Lambda dependencies. The runtime base is explicitly Temurin Ubuntu 24.04 without the inherited Pebble daemon. The actually served Swagger bundle is covered by HTTP regression tests; restoring the old bundle produces exactly one meaningful failure.

[Hosted CI 40248195136](https://github.com/MarvelousJade/paymentops/actions/runs/40248195136) at `cb0a2e7`: **52 application tests** and separate **9 Lambda tests** passed with zero failures/errors/skips; application image built. This includes the two LocalStack tests excluded from the local 50-test native suite. Raw bounded summary: `evidence/security-hosted-tests.txt`.

[Hosted security 40248195104](https://github.com/MarvelousJade/paymentops/actions/runs/40248195104) passed CodeQL, Dependency-Check for **both** Maven projects, and Trivy. Original report artifacts were downloaded and inspected. Trivy's OS/Java targets reported zero findings at the unchanged **HIGH/CRITICAL, ignore-unfixed** filter; image ID, raw-report hashes, data timestamps and job references are preserved in `evidence/security-hosted-verification.json`. Dependency-Check retains two non-blocking CPE matches: a Kafka broker-ACL documentation advisory rather than client-code defect, and an OpenTelemetry JavaScript advisory mismatched to the Java library. Neither was suppressed; rationale/primary references are in `evidence/security-remaining-triage.json`. Lambda dependency report contains no vulnerabilities. Dependency review is PR-only and was correctly skipped on push. Authenticated Sonatype analysis is not configured or claimed. Passing bounded scanner gates is not proof of absence of vulnerabilities.

A fresh packaged-application HTTP workflow after the updates passed authorize/capture/settle/reconcile/partial/full refund, conflict and ownership assertions. Direct SQL confirmed three balanced event-linked posting pairs, one settlement, full refund, restored 100/0 CAD balances and no unresolved exceptions. Runtime Swagger/OpenAPI returned 200 and served DOMPurify 3.4.16. Evidence: `security-demo-after-update.json` and `security-demo-database.txt`; jar/source identity is in the hosted verification summary. This is an additional correctness smoke, **not** another sample in the original five-run benchmark; historical timings are not reattributed to the updated dependencies.

[Subsequent CI 40248711117](https://github.com/MarvelousJade/paymentops/actions/runs/40248711117) at `ed9c50c` passed the same 52/9 suites, built and **started default db+app Compose**, waited for both containers to be healthy, fetched OpenAPI/Swagger and the patched sanitizer, checked the non-root user and absence of Pebble, then removed its isolated project containers and volumes. Security run 40248711141 also passed. Evidence: `evidence/security-compose-verification.txt`. This verifies default container startup, not optional Kafka/Registry/dashboards or full payment execution inside that container; the separately documented native packaged HTTP/SQL smoke verifies the payment workflow.

## Remaining gaps

Full optional `platform` Compose runtime, actual Kafka redelivery/rebalance/offset recovery, Schema Registry interoperability and real AWS deployment are not established. Hosted container build, LocalStack tests and configured security gates now pass. The optional platform's recovery/interop and any real deployment remain outside this evidence. Historical ledger load measurements are not new release measurements. Repository publication was subsequently authorized and completed (see journal); no deployment was performed. Initial screenshot inspection is not a usability study. More limitations: CHANGELOG.md.
