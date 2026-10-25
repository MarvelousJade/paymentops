# Performance evidence

## PaymentOps workflow

Five warm **local synthetic HTTP workflow** measurements are now recorded in [impact evidence](impact-and-resume-evidence.md): median 1205.397 ms, range 1186.547–1459.896 ms, with one warm-up excluded. Metadata, per-request samples, jar hash and database/restart correctness checks are retained. This is not a load test, throughput result or improvement claim.

No PaymentOps **load/Kafka** result has been recorded. `k6/paymentops.js` is a proposed workload with explicit correctness/failure thresholds, not evidence of a completed run. Use a named build and preserve its actual summary before making load claims.

Required evidence:

- commit SHA, Java/container versions, CPU/RAM, database/Kafka topology;
- VU and duration configuration;
- API p50, p95, p99 and request rate;
- maximum Kafka consumer lag and backlog-drain time;
- completed payments, duplicate postings, and reconciliation exceptions by
  classification.

## Verified legacy ledger baseline

The original ledger-only project completed a local k6 run using 100 virtual
users for 90 seconds, a 1:2 transfer-to-read mix, ten hot accounts, and a
Dockerized PostgreSQL instance on the same laptop as k6.

| Operation | p50 | p95 |
|---|---:|---:|
| Transaction-history read | 88 ms | 253 ms |
| Synchronous transfer | 312 ms | 978 ms |

That completed run recorded approximately 276 requests/second, zero request
errors, and 25,000 requests. It predates PaymentOps and must not be presented as
PaymentOps payment/Kafka performance.
