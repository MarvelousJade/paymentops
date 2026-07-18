---
name: verify
description: Build, launch, and drive ledger-service end-to-end (REST surface)
---

# Verify ledger-service

Surface: REST API on http://localhost:8080.

## Launch

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"   # needs JDK 21, not the system JDK 11
docker compose up -d db          # Postgres on host port 15432 (5432/5433 sit in Windows excluded-port ranges)
$env:JWT_SECRET = "<base64, ≥32 bytes>"   # e.g. [Convert]::ToBase64String((1..48 | % {Get-Random -Max 256}))
.\mvnw.cmd spring-boot:run       # ready when GET /actuator/health returns UP (~15 s)
```

## Drive

Full flow (curl): `POST /auth/register` → grab `accessToken` → `POST /accounts` ×2 →
`POST /accounts/{id}/deposits` → `POST /transfers` with `Idempotency-Key` header →
replay same key (same transfer id must come back) → `GET /accounts/{id}` balances →
`GET /accounts/{id}/statements/<yyyy-MM>` (CSV with opening/closing lines).

Good probes: no bearer → 401; foreign account → 404; amount "10.123" → 400;
oversized transfer → body `status: FAILED, failureReason: Insufficient funds`;
refresh-token reuse after rotation → 401 and the rotated-to token dies too (family kill).

## Gotchas

- Tests and Testcontainers use their own DB; the compose DB keeps state between runs — reuse emails fails with 409, suffix them.
- `src/test/resources/application.yml` shadows the main yml entirely; new main-yml properties must be mirrored there.
- Swagger UI: `/swagger-ui/index.html`.
