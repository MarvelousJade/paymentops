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

Dependency-Check's first NVD synchronization can take significant time. CI
should use a maintained mirror/cache for reliable operational use.
