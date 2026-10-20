# Backend release process

Production: **Dokploy on Lightsail Malaysia**, `notesweb-my-01` / `ip-172-26-24-177` (`172.26.24.177`). Application `_mkgSpRBPfNpAjBLu_zDB`. OrbStack is retired and is never a CD fallback. Spring Boot stays **3.5.16**; messaging/data contracts are unchanged.

## Gates

1. PR: Java 21 unit tests, disposable PostgreSQL/Redis/RabbitMQ integration tests, built-image readiness smoke, and Node CD regression tests. PRs have no production credentials.
2. Merged, current `main`: rebuild that revision for amd64/arm64; record `nntn/notes-app@sha256:…` in `backend-release`. Feature pushes publish candidates only. Direct main pushes cannot deploy.
3. `ProductBE` environment (restrict deployment branches to `main`): `DOKPLOY_API_KEY`; registry credentials retain their existing names. Never use the old `dokploy_URL` webhook or mutable `latest` as a deployment input. The API key must belong to Lightsail, with only the application/deployment and Docker/cluster read permissions needed by `scripts/ci/deploy.mjs`; verify the effective scope supported by Dokploy before issuing it.
4. Serial CD checks the exact node, application, previous healthy pinned digest, unchanged runtime settings and no competing deployment. It updates only the pinned image and replaces the legacy custom health override with the image's liveness check, then requests a uniquely named API deployment. No API mutation is automatically retried.
5. Require matching Dokploy deployment completion, one healthy Swarm replica, exact digest and OCI revision. Public GET gates check readiness, BE and FE same-origin auth polling (202/PENDING), and anonymous note access denial (401). Observe for another 120 seconds. Only then promote that digest to `latest`.

Readiness includes PostgreSQL, Redis and RabbitMQ; liveness checks application state only. Public health responses contain only status. Redis health uses PING because the application ACL deliberately denies INFO. CI runtime smoke is amd64; multiarch publishing does not imply an arm64 runtime test.

## Failure and rollback

`backend-deployment` retains sanitized `deployment.json` for 90 days: candidate/previous digests, revision, deployment IDs and rollback outcome. A failed release stays red even if rollback succeeds. Native Swarm health rollback remains an additional layer.

CD rolls back to the saved digest and prior health override, then repeats runtime/public gates, **only** when this run still owns the desired image/settings and no competing or active deployment exists. It preserves env, networks, volumes and data. Ambiguous POST outcomes, concurrent changes or failed rollback produce `manual_required`; inspect Dokploy history/runtime before retrying. Do not blindly rerun the workflow or force `docker service rollback` over an unrelated PreviousSpec.

Manual recovery: quiesce deployment automation, verify the Lightsail node and latest successful artifact, pin the known-good digest in Dokploy, deploy, then verify service/container health, readiness and public contracts. Leave current data volumes intact. Schema/data rollback requires a separately validated backup/restore plan; never restore an old OrbStack DB over new production writes.

Before first rollout: confirm off-VM encrypted backup and restore evidence, API key scope, ProductBE branch restriction and live API response shapes. Do not merge until PR CI and review pass. No schema migration, IAM change, Free→Paid upgrade or public database/admin exposure is part of this release.
