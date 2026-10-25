# Release, verification, and rollback

## GitHub environment setup

Create protected `staging` and `production` environments. Production must
require an approver who did not author the change.

| Name | Kind | Purpose |
|---|---|---|
| `STAGING_DEPLOY_HOOK` | staging secret | Deploy the supplied immutable image |
| `STAGING_URL` | staging variable | Smoke-test base URL |
| `PRODUCTION_DEPLOY_HOOK` | production secret | Deploy selected image |
| `PRODUCTION_ROLLBACK_HOOK` | production secret | Restore last known good image |
| `PRODUCTION_URL` | production variable | Health/telemetry verification |

Runtime secrets (`JWT_SECRET`, database credentials, webhook signing secret,
and any OIDC credentials) belong in the deployment secret store, not workflow
files or the repository.

## Manually authorized staging release

Status: configuration and proposed procedure, not evidence of an executed deployment. No release is triggered automatically by a main-branch push.

1. Confirm CI, CodeQL, dependency review/check, and Trivy are green.
2. Merge to `main`.
3. Explicitly dispatch **Release to staging** on the intended main SHA. The workflow repeats tests, builds the container, and publishes
   `ghcr.io/{owner}/{repo}:{commit-sha}`.
4. The protected staging job deploys that exact image.
5. Health must report `UP`; Prometheus must expose JVM telemetry.
6. Run one synthetic authorize/capture/settle flow and confirm its trace,
   timeline, balanced posting, webhook delivery, and reconciliation result.

## Production promotion

1. Record the verified SHA and current production SHA.
2. Start **Promote or roll back**, passing the verified SHA as `image_tag` and
   current SHA as `rollback_tag`.
3. The production environment approval is the change-control gate.
4. After deployment, the workflow checks health and PaymentOps telemetry.
5. Watch error rate, p95 latency, Kafka lag, retry/DLT volume, database pool,
   and reconciliation exceptions for the agreed observation window.

## Rollback criteria

Roll back for failed health/telemetry verification, sustained error/latency
regression, growing unprocessable lag, an unexpected duplicate financial
effect, migration incompatibility, or missing telemetry needed to operate
safely. The workflow invokes the rollback hook automatically when verification
fails.

Database migrations are forward-compatible and append-only. Never run an
automatic destructive database rollback. Restore the previous application
image, then remediate data through a reviewed forward migration or compensating
entry.

## After-hours drill

Proposed roles (not claims of a staffed operation):
- Owner: release operator
- Observer/approver: second reviewer
- Escalation: designate a responsible maintainer before any shared deployment

The drill deploys a selected SHA to staging, injects one consumer crash before
offset commit, verifies the duplicate metric and single posting, checks
reconciliation, and deploys the previous SHA using the rollback hook. Record
timestamps, SHAs, dashboard links, and deviations in the release ticket.
