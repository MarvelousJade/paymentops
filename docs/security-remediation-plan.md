# Hosted security remediation

Starting point: `eb26539` on main; clean working tree. Work branch: `shaoyu/rework`.

PaymentOps remains a synthetic payment-operations portfolio application for developers and operations learners, not a real-money service. This increment preserves its authorize/capture/settle/reconcile/refund workflow and demonstrates dependency management, container supply-chain review and fail-closed CI. Suggested motivation to personalize: “I wanted to learn how to keep a working financial workflow verifiable while maintaining its dependencies.” No new business features, production infrastructure or deployment are included.

## Acceptance and checklist

- [x] Reproduce and inventory the reported Java/container findings; inspect Lambda dependencies too.
- [x] Upgrade the smallest compatible patched dependency set and use an explicit supported runtime base without unnecessary daemons.
- [x] Keep both Maven test suites passing; resolve any upgrade regressions with tests.
- [x] Make Dependency-Check complete against fresh official NVD data without requiring a privately provisioned API key or weakening its CVSS/error gate.
- [ ] Run hosted CI, CodeQL, dependency scanning and rebuilt-image scanning; inspect real reports, not just job labels.
- [ ] Commit evidence/journal, integrate corrected changes into main, push and inspect final status. Never claim that passing scanners proves absence of vulnerabilities.

## Initial evidence

Hosted security run 40246048460 reported 23 Java findings (17 HIGH, 6 CRITICAL), plus four HIGH Go findings in `/usr/bin/pebble` inherited from the floating Temurin Ubuntu 26.04 image. Dependency-Check 12.2.2 failed its empty-database initialization on NVD API HTTP 429. CodeQL succeeded. These were actual scan/update failures, not intentionally introduced defects or observed attacks.

Maven Central's 4.0.9 Boot BOM manages patched Framework 7.0.10, Tomcat 11.0.27, Netty 4.2.19.Final, JDBC 42.7.14 and Jackson 2.21.7/3.1.7. Kafka 4.1.2 still requests lz4 1.10.1, so that artifact needs a narrowly scoped managed override. Prefer this maintenance release to a 4.1/4.2 migration. Adoptium's explicit `21-jre-noble` Dockerfile uses supported Ubuntu 24.04 without the unrelated Pebble service.

References: [Boot managed versions](https://docs.spring.io/spring-boot/4.0/appendix/dependency-versions/coordinates.html), [Spring advisory](https://spring.io/security/cve-2026-47884), [Temurin noble image source](https://github.com/adoptium/containers/blob/main/21/jre/ubuntu/noble/Dockerfile), [Dependency-Check 13 release](https://github.com/dependency-check/DependencyCheck/releases/tag/v13.0.0), [Dependency-Check configuration](https://dependency-check.github.io/DependencyCheck/dependency-check-maven/configuration.html).
