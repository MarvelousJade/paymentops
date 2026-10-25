# Changelog

## Focused workflow release — 2026-10-25

- Preserved the pending PaymentOps expansion and existing ledger APIs on `shaoyu/rework` (`011bfe6`), after documenting scope/references (`bc8caf2`). No artificial reconstruction of its earlier development history.
- Fixed concurrent event-claim transaction failure (`ab86d88`) and lost settlement evidence following early refunds (`ccff3e4`).
- Made db+app/local transport the default, optionalized platform infrastructure and added an assertion-driven HTTP demo (`02ac6a1`). Publication requires explicit workflow dispatch.
- Integrated corrected controlled fault investigations with regression coverage through merge `a249277`; preserved checkpoints/repairs, no unresolved faulty code.
- Isolated repeated expired-token fixtures (`e772520`) and controlled initial dispatch in integration tests (`7e14b85`). Added concurrent-create, insufficient-funds and cancelled-payment coverage (`c8a3b85`).
- Recorded local workflow measurements, direct database/restart verification, programmatic browser interaction and interview/resume evidence. Historical ledger measurements remain separately qualified.

## Known limitations

- Synthetic CAD only; no real payment provider/card storage, authorization reservation, chargebacks or fee accounting.
- Single application/dispatcher intended; no multi-instance outbox leasing or globally ordered cross-topic processing guarantee.
- Pending refund amounts are not reserved at API admission; final validation may reject a later queued refund. Rejected external settlement requires investigation, not automatic financial reversal/refund.
- Reconciliation loads data in memory and compares capture/settlement amounts; no separate currency-mismatch classification or scalable pagination.
- Kafka/Registry/dashboards are optional configurations; actual broker recovery and container execution remain unverified here. Registry registration is not producer schema enforcement.
- Simulated webhooks; no audited delivery requeue API. Receiver idempotency/replay protection and secure endpoint management would be required for real delivery.
- Local identity, in-memory rate limiting, public health/metrics, development secrets/defaults and exposed Compose ports require a production threat model before shared deployment. No production-readiness claim.
- Local checks do not establish hosted CI/security/deployment success; publication-time results are available in GitHub Actions. Dependency/image findings are not claimed clean. Repository main was subsequently pushed with owner authorization; no deployment was performed. No repository distribution licence has been selected; public source availability does not grant an open-source licence.
- No Docker engine/WSL distribution: legacy LocalStack integration and Docker lifecycle are not covered by the native PostgreSQL run.
- Browser checks are programmatic and initial-screen visual inspection, not human usability/accessibility/mobile evaluation.
