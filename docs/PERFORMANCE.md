# Performance evidence

## PaymentOps workflow

No PaymentOps load result has been recorded in this repository yet. Run
`k6/paymentops.js` against a named build and replace this statement with the
generated summary plus environment details. No estimated or aspirational
numbers are reported.

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
