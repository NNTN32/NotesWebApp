# Backend release process

Production: **CapRover 1.15.1 (Docker Swarm) on Lightsail Malaysia**, VM `notesweb-my-02-caprover` (static IP behind Cloudflare only). The backend is the CapRover app `server` (`https://server.nhannotes.id.vn`, internal `http://server:8081`). Images live in the **private** GitHub Container Registry `ghcr.io/nntn32/notes-app`. Docker Hub and Dokploy are retired; Spring Boot stays **3.5.16**.

## Gates

1. PR: Java 21 unit tests, disposable PostgreSQL/Redis/RabbitMQ integration tests, built-image readiness smoke, and Node CD regression tests. PRs have no production credentials.
2. Merged, current `main`: rebuild that revision for amd64/arm64 with `REVISION=<commit>` and push to GHCR with the workflow's own `GITHUB_TOKEN` (`packages: write`); record `ghcr.io/nntn32/notes-app@sha256:…` in `backend-release`. Feature pushes publish candidates only. Direct main pushes cannot deploy (`release-policy`).
3. `ProductBE` environment (deployment branches: `main` only) holds one secret, `CAPROVER_APP_TOKEN`: the deploy token of the CapRover app `server`. It can only start a deploy of that one app; it cannot read config, env or other apps.
4. `deploy` resolves `ghcr.io/nntn32/notes-app:latest` (only ever a digest that passed this job) as the rollback target, then asks CapRover to deploy the exact new digest (detached, so Cloudflare's 100 s proxy limit never cuts the request). CapRover pulls it from GHCR with its own read-only registry credential.
5. Public gates: `/actuator/info` must report the new commit (proves the new container answers), readiness UP, BE and FE same-origin auth polling `202 PENDING`, anonymous `/notes/listNotes` `401`. Until they first all pass CD keeps retrying for up to 10 minutes (the FE may be releasing at the same time); then they must hold for 120 s. Only then is the digest promoted to `latest`.

Readiness includes PostgreSQL, Redis and RabbitMQ; liveness checks application state only (Swarm uses it via the image `HEALTHCHECK`). Public health responses contain only status and `/actuator/info` only the revision. All GitHub Actions are pinned to full commit SHAs. `main` is protected: PR required, `build-and-test` and `deployment-tests` must pass, no force-push/deletion.

## Running a release

- **Release:** merge a reviewed PR normally. CI publishes, deploys and promotes `latest`; watch the `deploy` job and the `backend-deployment` artifact.
- **No deploy:** put `[skip deploy]` in the PR title for changes that do not alter the running application (CI, docs, tests). Never put it, even negated, in a runtime PR's title.
- **After a release:** `https://server.nhannotes.id.vn/actuator/info` shows the merged commit and GHCR `latest` has the same index digest with amd64 and arm64.

## Failure and rollback

`backend-deployment` keeps a sanitized `deployment.json` for 90 days: new/previous digests, revision, status, rollback outcome and, if CapRover rejected the request, only `CapRover deploy HTTP <code>` or `status <code>`. Response bodies and tokens are never written or printed.

If the new revision never appears or any gate fails, CD deploys the previous verified digest and waits for every public contract on a different revision (`rollback: passed`). The run stays red. `no_previous_release` (first release) or `manual_required` means: open the CapRover dashboard → app `server` → Deployment, pick the last good version (or deploy `ghcr.io/nntn32/notes-app@<digest from the last green backend-deployment>`), then check `/actuator/info` and readiness. Data volumes (`notes-postgres`, `notes-redis`, `notes-rabbitmq`) are never touched by CD. Schema/data rollback needs a separately validated backup/restore.

## Credentials

- `CAPROVER_APP_TOKEN`: only in `ProductBE`. Rotate on suspicion or maintainer change: dashboard → app `server` → Deployment → regenerate the token, update the secret, run a release.
- GHCR pull credential on CapRover: a classic GitHub token with **only** `read:packages`, stored in CapRover → Cluster → Docker Registries. Rotate before it expires.
- No long-lived registry push secret exists: CI pushes with the per-run `GITHUB_TOKEN`.
- `DISCORD_CI_WEBHOOK` (repository secret, Discord `#ci_be-alert`): the final `notify` job posts one message per run, success, failure or cancelled, with each job's result and the run link (`scripts/ci/notify-discord.mjs`). Fork and Dependabot runs have no secret and skip. Rotate by regenerating the webhook in Discord and updating the secret.
- CapRover admin password and the Cloudflare Origin certificate key stay on the VM / in the owner's password manager, never in GitHub.
