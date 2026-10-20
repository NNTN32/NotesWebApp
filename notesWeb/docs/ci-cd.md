# Backend release process

Production: **Dokploy on Lightsail Malaysia**, `notesweb-my-01` / `ip-172-26-24-177` (`172.26.24.177`). Application `_mkgSpRBPfNpAjBLu_zDB`. OrbStack was removed on 20/10/2026 after a final `pg_dumpall` (app DB `NoteApp` + Dokploy DB) was saved off-machine by the owner, and is never a CD fallback. Spring Boot stays **3.5.16**; messaging/data contracts are unchanged.

## Gates

1. PR: Java 21 unit tests, disposable PostgreSQL/Redis/RabbitMQ integration tests, built-image readiness smoke, and Node CD regression tests. PRs have no production credentials.
2. Merged, current `main`: rebuild that revision for amd64/arm64; record `nntn/notes-app@sha256:…` in `backend-release`. Feature pushes publish candidates only. Direct main pushes cannot deploy; `release-policy` retries the merged-PR lookup for about a minute because GitHub can lag in linking a fresh merge commit to its PR.
3. `ProductBE` environment (restrict deployment branches to `main`): `DOKPLOY_API_KEY`; registry credentials retain their existing names. Never use the old `dokploy_URL` webhook or mutable `latest` as a deployment input. The key is an API key of the Dokploy **owner**: a Member role is rejected with HTTP 401 on `application.update` (run #99), and per-service roles need an Enterprise license. See [Deploy credential](#deploy-credential) for the compensating controls.
4. Serial CD checks the exact node, application, previous healthy pinned digest, unchanged runtime settings and no competing deployment. It updates only the pinned image and replaces the legacy custom health override with the image's liveness check, then requests a uniquely named API deployment. Mutations are not blindly retried: the only retry is one `application.deploy` request after a 5xx/transport failure, and only when no deployment with this release ID exists (see Failure and rollback).
5. Require matching Dokploy deployment completion, one healthy Swarm replica, exact digest and OCI revision. Public GET gates check readiness, BE and FE same-origin auth polling (202/PENDING), and anonymous note access denial (401). Observe for another 120 seconds. Only then promote that digest to `latest`.

Readiness includes PostgreSQL, Redis and RabbitMQ; liveness checks application state only. Public health responses contain only status. Redis health uses PING because the application ACL deliberately denies INFO. Full CI runtime/integration smoke is amd64. After publishing, each architecture's child digest is pulled and checked (OCI revision label equals the commit, `java -version` runs under amd64 and arm64); this is not an arm64 integration test. All GitHub Actions are pinned to full commit SHAs. `main` is protected by a ruleset: PR required, `build-and-test` and `deployment-tests` must pass on an up-to-date branch, no force-push/deletion, no bypass.

## Running a release

- **Release:** merge a reviewed PR with a normal merge commit. CI then publishes, deploys and promotes `latest` by itself; watch the `deploy` job and the `backend-deployment` artifact.
- **No deploy:** put `[skip deploy]` in the PR title for changes that do not alter the running application (CI, docs, tests). The merge commit message carries the PR title and CI checks the whole message, so the default merge title is fine. Tests still run; on the `main` run confirm `publish` and `deploy` show *skipped* and production keeps its pinned digest. Never put the marker, even negated, in a runtime PR's title.
- **Branches:** start from an up-to-date `main`, keep one topic per PR, and delete the head branch after merge.
- **After a release:** confirm the Dokploy deployment titled `BE-<run>-<attempt>` is Done, the pinned image equals the new digest, public readiness is UP and `latest` on Docker Hub has the same index digest with amd64 and arm64.
- **Re-running:** re-run only the failed `deploy` job, and only after the `failure` field and Dokploy state are understood (next section).

## Failure and rollback

`backend-deployment` retains sanitized `deployment.json` for 90 days: candidate/previous digests, revision, deployment IDs, rollback outcome and, on failure, a sanitized `failure` such as `Dokploy application.update HTTP 401` (route and status only; never response bodies). A failed release stays red even if rollback succeeds. Native Swarm health rollback remains an additional layer.

CD rolls back to the saved digest and prior health override, then repeats runtime/public gates, **only** when this run still owns the desired image/settings and no competing or active deployment exists. It preserves env, networks, volumes and data. If `application.deploy` fails with a 5xx or transport error (for example Cloudflare 522), CD first looks for a deployment titled with this release ID for up to 30 seconds, continues with it if found, and otherwise retries that one request once; any other ambiguous POST outcome, concurrent change or failed rollback produces `manual_required`. After such a failure Dokploy can pin the new digest while Swarm still runs the old one, so compare the pinned image with the running service before re-running; inspect Dokploy history/runtime before retrying. Do not blindly rerun the workflow or force `docker service rollback` over an unrelated PreviousSpec.

Manual recovery: quiesce deployment automation, verify the Lightsail node and latest successful artifact, pin the known-good digest in Dokploy, deploy, then verify service/container health, readiness and public contracts. Leave current data volumes intact. Schema/data rollback requires a separately validated backup/restore plan; never restore the 20/10 OrbStack dump over new production writes.

## Deploy credential

`DOKPLOY_API_KEY` has full owner power, so treat it as the most sensitive CD secret.

- Store it only as an environment secret of `ProductBE`, restricted to `main`; never in repository secrets, PR workflows, logs, files or chat. Use a key dedicated to this pipeline (for example `github-be-deploy`) and not for anything else.
- Rotate it on a schedule and immediately after any suspected exposure, a maintainer change or a failed-deploy investigation that touched it: create the new key, update the secret, then revoke the old key in Dokploy (Profile → API/CLI).
- Enable 2FA on the owner account and review Dokploy audit logs after releases. The script is hard-pinned to one endpoint and application, so a leaked workflow cannot redirect it elsewhere, but a leaked key itself can act as owner.
- Do not recreate a Member CI user unless Dokploy grants it update/deploy rights on one service without node-wide Docker access.

After each release, confirm the publish job's per-architecture smoke step is green and `release.json` in `backend-release` lists both `linux/amd64` and `linux/arm64` before trusting the deployed digest.

Before first rollout: confirm off-VM encrypted backup and restore evidence, API key scope, ProductBE branch restriction and live API response shapes. Do not merge until PR CI and review pass. No schema migration, IAM change, Free→Paid upgrade or public database/admin exposure is part of this release.
