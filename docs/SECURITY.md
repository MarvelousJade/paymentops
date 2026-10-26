# Security and vulnerability remediation

## Data rules

Only synthetic account identifiers and `tok_...` payment tokens are accepted.
Never submit card numbers, bank credentials, real personal information, API
keys, or third-party branding. Webhook history omits signing secrets and
signatures.

## Implemented controls

- short-lived scoped JWT access tokens and rotating hashed refresh tokens;
- merchant/operator/auditor role separation with no public role selection;
- authorization and idempotency headers on modifying payment APIs;
- source-account ownership checks and non-disclosing `404` responses;
- Bean Validation and stable problem codes/correlation IDs;
- per-principal modifying-request rate limits;
- environment-injected signing/JWT secrets;
- structured logs without authorization headers or request bodies;
- an unprivileged application user in the runtime container;
- append-only ledger, payment timeline, audit, and reconciliation actions;
- CodeQL, dependency review, OWASP Dependency-Check, and Trivy gates.

The in-memory rate limiter is suitable for the single-instance local demo.
Multi-instance production must replace it with a shared atomic store before
horizontal scaling.

## Remediation policy

1. Triage a finding for reachability, affected version, exploitability, and
   compensating controls.
2. Critical exploitable findings block release and begin immediate remediation.
   High findings block the configured quality gate.
3. Upgrade the smallest safe dependency set, add a regression test when
   behavior is involved, and rerun all four scanners.
4. Suppress only proven false positives. A suppression must name the finding,
   evidence, owner, expiry, and review date.
5. Rebuild from a clean base, rescan the digest, deploy the immutable SHA to
   staging, and verify health/telemetry.
6. Record discovery, decision, patch, verification, and release references in
   the vulnerability ticket.

## Reproducible dependency scanning

CI scans **both** `pom.xml` and the independent `settlement-lambda/pom.xml`
with Dependency-Check 13.0.0. It uses NVD's official JSON 2.0 yearly/modified
feeds instead of paginating the rate-limited API:

```bash
./mvnw -B -DskipTests org.owasp:dependency-check-maven:13.0.0:check \
  '-DnvdDatafeedUrl=https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-{0}.json.gz' \
  -DfailBuildOnCVSS=7 -DfailOnError=true -Dformat=ALL
# Repeat with -f settlement-lambda/pom.xml.
```

The first full feed import takes several minutes. CI restores a versioned,
daily database cache, then leaves automatic freshness checks enabled (the
tool's default four-hour update interval). The feeds can lag the live API;
review their reported last-modified timestamps. Update/download/analysis
errors still fail the job, and either project's blocking finding fails it.
Reports are retained even on failure. No NVD key, commercial mirror or
suppression is required. Maven may need `-DpathToCore=<dotnet executable>`
when .NET 8 is outside PATH; the assembly analyzer needs that runtime even
for native DLLs nested in Java archives. CI installs it explicitly rather
than disabling an analyzer. Authenticated Sonatype Guide/OSS Index is not
configured or claimed; the tool reports that limitation.

The application uses Boot 4.0.9 with scoped patched transitive overrides.
The standalone Lambda has its own SDK/Jackson BOMs and only ships the HTTP
transport used by its synchronous client. The explicit Temurin
`21-jre-noble` runtime avoids an unrelated inherited Pebble daemon. Match
`springdoc.swagger-ui.version` in runtime/test configuration to the managed
webjar whenever updating it; HTTP tests verify the real served bundle.
See the journal and `evidence/security-remaining-triage.json` for unsuppressed
non-applicable CPE matches. These remain visible rather than being hidden.
Passing gates is not proof of zero vulnerabilities or production readiness.
