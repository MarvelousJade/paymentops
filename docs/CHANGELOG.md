# Changelog

## Focused workflow release — 2026-10-25

- Preserved the pending PaymentOps expansion and existing ledger APIs on `shaoyu/rework` (`011bfe6`), after documenting scope/references (`bc8caf2`). No artificial reconstruction of its earlier development history.
- Fixed concurrent event-claim transaction failure (`ab86d88`) and lost settlement evidence following early refunds (`ccff3e4`).
- Made db+app/local transport the default, optionalized platform infrastructure and added an assertion-driven HTTP demo (`02ac6a1`). Publication requires explicit workflow dispatch.
- Integrated corrected controlled fault investigations with regression coverage through merge `a249277`; preserved checkpoints/repairs, no unresolved faulty code.
- Isolated repeated expired-token fixtures (`e772520`) and controlled initial dispatch in integration tests (`7e14b85`). Added concurrent-create, insufficient-funds and cancelled-payment coverage (`c8a3b85`).
- Recorded local workflow measurements, direct database/restart verification, programmatic browser interaction and interview/resume evidence. Historical ledger measurements remain separately qualified.

## Security remediation — 2026-10-26

- Patched Boot 4.0.x and affected transitive libraries; retained Swagger UI with a patched sanitizer and HTTP asset regression tests (`83c8179`). Updated the independent Lambda SDK/Jackson BOMs and removed its unused async transport.
- Selected explicit Temurin Ubuntu 24.04 without the inherited, unnecessary Pebble daemon.
- Repaired NVD initialization with official JSON 2.0 feeds, an automatically refreshed cache, explicit analyzer prerequisites and both Maven projects scanned; retained reports without weakening gates (`cb0a2e7`).
- Hosted verification at `cb0a2e7`: 52 application tests, 9 separate Lambda tests, image build, CodeQL, both dependency scans and fixed HIGH/CRITICAL image gate pass. Non-applicable CPE matches remain visible with documented triage. See `verification.md` and `evidence/security-hosted-verification.json`.
- Preserved the original scanner failures and five-run performance evidence as historical records. A fresh post-upgrade packaged HTTP/SQL workflow passed; original timings are not claimed to describe the new dependency set.

## Known limitations

- Synthetic CAD only; no real payment provider/card storage, authorization reservation, chargebacks or fee accounting.
- Single application/dispatcher intended; no multi-instance outbox leasing or globally ordered cross-topic processing guarantee.
- Pending refund amounts are not reserved at API admission; final validation may reject a later queued refund. Rejected external settlement requires investigation, not automatic financial reversal/refund.
- Reconciliation loads data in memory and compares capture/settlement amounts; no separate currency-mismatch classification or scalable pagination.
- Kafka/Registry/dashboards are optional configurations; their runtime and actual broker recovery remain unverified here. Default db+app container startup is verified by hosted Compose smoke at `ed9c50c`. Registry registration is not producer schema enforcement.
- Simulated webhooks; no audited delivery requeue API. Receiver idempotency/replay protection and secure endpoint management would be required for real delivery.
- Local identity, in-memory rate limiting, public health/metrics, development secrets/defaults and exposed Compose ports require a production threat model before shared deployment. No production-readiness claim.
- Passing local/hosted checks does not establish production security or deployment success. Hosted configured security gates now pass; their filters, remaining non-applicable CPE matches and coverage limitations are explicit in the verification record. Repository main was subsequently pushed with owner authorization; no deployment was performed. No repository distribution licence has been selected; public source availability does not grant an open-source licence.
- No local Docker engine/WSL distribution: Docker lifecycle evidence comes from hosted CI. At `ed9c50c`, 52 application tests including LocalStack and 9 Lambda tests pass, and default Compose startup/health/permissions/Swagger assets pass. Full optional platform runtime is still unverified.
- Hosted security at `0ef3009` failed on image findings and NVD 429; this was reproduced and repaired, with passing scans at `cb0a2e7`. Historical failures remain in evidence, not an unresolved remediation claim. No scanner gates were weakened.
- Browser checks are programmatic and initial-screen visual inspection, not human usability/accessibility/mobile evaluation.
