# Backend CI/CD

- Push `feature/**`: unit tests → local Docker image → disposable PostgreSQL/Redis/Kafka/RabbitMQ + application smoke/context test → multi-platform push `feature-<branch>` and `sha-<full commit>`. Never writes latest or calls production webhook.
- PR to main: same validation; no DockerHub login, publish or deploy. Fork PRs do not require production secrets.
- Push main: validate again. Only a commit associated with a merged PR targeting this repository/main is eligible for publication. Direct main pushes run tests but cannot publish latest or deploy. Supports GitHub merge/squash/rebase PR merge SHA.
- Eligible main: publish SHA/main tags → check current main SHA → promote exact digest to latest → check current main SHA again → call Dokploy webhook. Tests/build failures prevent promotion and deployment. Main runs are not cancelled mid-promotion.

Secrets (existing names): DOCKERHUB_USERNAME, DOCKERHUB_TOKEN, dokploy_URL. Dummy CI fixtures replace production DB/broker/JWT/Cloudinary credentials. Context test and HTTP smoke do not cover full business job delivery. Multi-platform release rebuilds the tested source; only amd64 image gets the runtime smoke in CI.

## Production prerequisite

Dokploy must pull `nntn/notes-app:latest` on deployment, not an old digest/immutable tag. Current hardening deployment is pinned to be-acl-e8db05b digest. Do not switch to latest until the PR containing Redis ACL client fix is merged and the new main pipeline has published latest successfully; otherwise the old image can break authenticated Redis workers. Then change the Dokploy image reference and trigger the validated deployment. Webhook success only means deployment requested; check Dokploy task/image digest and API health afterwards. CI does not silently modify the production image reference.

Require PRs and the `build-and-test` status check in main branch protection. Merge through GitHub; do not cherry-pick directly to main.
