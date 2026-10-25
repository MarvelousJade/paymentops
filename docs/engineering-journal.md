# Engineering journal

## 2026-10-25 — baseline and environment hurdle

- Starting commit: `9316c46`. The authorized working tree already contained a large PaymentOps implementation, infrastructure and docs. Preserve that provenance rather than manufacture earlier increments. `.claude/skills/verify/SKILL.md` is unrelated and stays uncommitted; existing staged `AGENTS.md` is preserved.
- Problem/constraint: neither Java nor Docker was on PATH; Windows command discovery found neither installation and WSL reported no distributions. This is an environment constraint, not an application defect.
- Approach: downloaded portable Temurin JDK 21 and PostgreSQL 16.4 binary artifact outside the repository. Avoid installing Docker/WSL or changing system configuration. Testcontainers remains the default; an explicit external disposable DB option will permit genuine SQL/transaction testing here.
- Baseline observation: unchanged pending application compiled; `./mvnw -B -Dtest=MoneyTest,AuthServiceTest,RateLimiterTest test` passed 9 tests, 0 failed/errors/skipped (Maven 1:38 including downloads). This does not verify the payment workflow. Historical `target` reports were not treated as current results.
- Tradeoff: native PostgreSQL verifies real database semantics but not container startup, Kafka or Compose. Portable tool files/data remain outside Git. Lesson: record environment limits and seek a safe equivalent test layer instead of silently skipping integration tests.
- Personal investigation notes: not supplied. Statements here describe repository work/evidence, not claims about what the owner personally investigated.
