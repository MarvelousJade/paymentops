# Five-minute demonstration

## Prerequisites and safety

Java 21/PostgreSQL 16 (or Docker/Compose), Node 22+, a loopback application at port 8080. Use a disposable database; the fixture creates users, deposits and immutable financial history, intentionally not deleted afterward. No real money or identity information. The script refuses remote URLs. A test database and a demo database must be separate.

## One-time operator provisioning

Choose a password for this local fixture and register through Swagger `POST /auth/register`:

```json
{"email":"demo-operator@example.test","password":"<your-local-demo-password>","fullName":"Synthetic Operator"}
```

Using a database administrator connection **to the disposable demo database only**:

```sql
UPDATE customers SET role = 'OPERATOR' WHERE email = 'demo-operator@example.test';
```

For Compose: `docker compose exec db psql -U ledger -d ledger`, then execute the SQL. Public clients cannot set roles. The registration token remains a merchant token; log in again after promotion. Do not expose a role-elevation endpoint for convenience.

## Demonstration script (five minutes)

1. Start the application per README; wait for `/actuator/health` to return `UP`. First JVM/migration startup can take tens of seconds. Confirm `/operations.html` and Swagger are served.
2. Set operator credentials and run:

```bash
export OPERATOR_EMAIL=demo-operator@example.test
export OPERATOR_PASSWORD='<your-local-demo-password>'
node scripts/demo.mjs
```

PowerShell equivalent: `$env:OPERATOR_EMAIL='demo-operator@example.test'`; `$env:OPERATOR_PASSWORD='<your-local-demo-password>'`; `node scripts/demo.mjs`.

3. Expect finalStatus `REFUNDED`, finalBalances `["100.00","0.00"]`, 0 open exceptions for that payment, one capture-posted timeline event and two completed refunds. Every HTTP status, replay ID, final balance and required timeline event is asserted. No timing threshold is claimed. Keep the printed payment/correlation IDs.
4. Log in as the operator in Swagger. Paste its fresh access token into the operations console, click Connect, search the correlation ID and refresh exceptions. Tokens stay in the page input; don't place them in URLs or screenshots.
5. Optional discrepancy: use Swagger operator endpoint `POST /api/v1/operations/reconciliation/settlements` with printed payment ID, unique externalReference, amount `29.00`, currency `CAD`, status `SETTLED`; run reconciliation. Expect duplicate and amount-mismatch exceptions, not an automatic financial correction. This intentionally adds evidence; do not edit/delete history. Do this after timing runs, not during equivalent-work measurements.

## Repeated measurement

Use one warm-up, then five sequential `node scripts/demo.mjs` invocations, each redirected to a separate JSON file. Each invocation creates equivalent fresh users/accounts and one payment; the database grows and is not cold-reset. Record source SHA, jar hash, versions, CPU, raw files and poll settings. Timings include HTTP polling (50 ms), outbox polling (250 ms), local database work and correctness checks. They are not server latency/throughput. Report median and range, not p95 from five samples. Token/password values must not enter committed evidence.

## Failure cases and backup plan

- 401: missing/wrong operator password; 403: role was not promoted or token predates promotion.
- Timeout/REJECTED/REVIEW_REQUIRED: inspect risk configuration and outbox rows; do not weaken assertions to make a demonstration green.
- Database connection failure: confirm host/port/database and migrations; never point tests at a real account database.
- No Docker: use native PostgreSQL and the external test option in `verification.md`.
- No runtime access during an interview: show a saved `docs/evidence/demo-*.json` result, trace IDs/timeline in code, and replay the targeted regression tests when infrastructure returns. Explain that saved output is local synthetic evidence, not a live production demonstration.

## Optional browser and database checks

For independent database verification, resolve the Maven PostgreSQL driver then run `java -cp <postgresql-jdbc.jar> scripts/VerifyDemo.java <printed-payment-uuid> ...` with explicit loopback DB_URL/DB_USER/DB_PASSWORD. The script is read-only and checks exactly three balanced event-linked postings, one external settlement, fully refunded totals, final balances and no open exception. Repeat after restarting the application to inspect saved state.

For programmatic console interaction, start a **dedicated disposable** headless Chrome (do not expose its debugging port or attach to a personal browser):

```bash
chrome --headless --disable-gpu --no-first-run --user-data-dir=<temporary-dedicated-directory> \
  --remote-debugging-port=9223 http://localhost:8080/operations.html
# In another shell, with operator env vars already set:
node scripts/console-smoke.mjs <saved-demo-json>
```

Chrome executable name/path varies by platform. Close the dedicated browser afterward. The script verifies error/success rendering, correlation search/repeat, empty-state refresh, reconciliation, 404 replay error and retained correlation input; it clears the token input before disconnecting. Run before importing the optional discrepancy, since the empty-state assertion expects no exceptions.

Actual verification distinguishes scripted HTTP, programmatic browser interaction, initial rendered screenshot inspection and human interaction (not performed). See `verification.md`.
