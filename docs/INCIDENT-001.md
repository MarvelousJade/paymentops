# Duplicate delivery scenario — hypothetical operator report

This is a hypothetical report, **not an observed Kafka crash, executed fault drill or production incident**. The original pending document contained a scenario-relative timeline; those timestamps were illustrative, not measurements. No on-call ownership or customer impact is asserted.

Scenario: a consumer commits its database work but crashes before acknowledging the message. A broker may redeliver it. Expected behavior is a harmless duplicate with one financial posting and retained timeline evidence.

Observed local evidence: `PaymentOpsFlowTest` routes a capture event twice and verifies one posting. `EventIdempotencyTest` exercises concurrent database claims and rollback/retry. BUG-001 in `engineering-journal.md` documents an actual PostgreSQL transaction error found and repaired during repository verification. These checks do not establish Kafka offset/crash behavior.

Future broker drill: start Kafka, preserve topic/partition/offset, terminate the consumer after commit and before acknowledgement, restart, and query processed-events/postings/balances. Record real logs and timestamps before claiming that drill was completed. See `RUNBOOKS.md` for diagnostic steps.
