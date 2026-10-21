# Secrets & credentials

Rule: local and production use the **same variable names**, different values. Values never go in git, chat, or PR text.

| Where | Source of values |
|---|---|
| Local | `notesWeb/.env` (copy from `.env.example`; gitignored) and the defaults in `application.yml` (`localhost`, `postgres`, `guest`) |
| Production | Dokploy environment of each service only |
| CI | GitHub secrets: `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, `DOKPLOY_API_KEY`. DB, Redis and Rabbit credentials are **not** in GitHub |

## Production identities (rebuilt 21/10/2026)

Each data service has its own admin and a narrow app account. `Notes_Server` only ever uses the app accounts.

| Service (Dokploy appName) | Admin, ops only | App account (`Notes_Server` env) |
|---|---|---|
| `notes-postgres` (`nnnoteswebapp-notespostgres-rbrlzd-lbjr8e`) | `postgres` superuser, password in Dokploy General | `notes_app`: owner of DB `NoteApp`, no superuser/createdb/createrole → `DB_USERNAME`, `DB_PASSWORD` |
| `notes-redis` (`nnnoteswebapp-notesredis-ztd7pp-cae4dt`) | `default` with `+@all`, password = Dokploy `REDIS_PASSWORD` | `notes_redis`: keys `auth: notes: media: note: session: grace: login:`, `-@admin -@dangerous` → `SPRING_DATA_REDIS_USERNAME`, `SPRING_DATA_REDIS_PASSWORD` |
| `notes-rabbitmq` (`nnnoteswebapp-notesrabbitmq-9fu2ti`) | `rabbit_admin` (administrator) | `notes_mq`, vhost `/`, no tags → `RABBIT_USERNAME`, `RABBIT_PASSWORD` |

Also on `Notes_Server`: `JWT_SECRET`, `CLOUDINARY_*` (unique to production). `fe` reaches the API through `API_UPSTREAM=http://nnnoteswebapp-notesserver-ehbjxt:8081`.

### Redis specifics

Redis runs with args `redis-server /data/notesweb-redis.conf`; users live in `/data/notesweb-users.acl` (SHA-256 hashes only). The Dokploy password field and `REDIS_PASSWORD` are **not** read by Redis after that; keep them equal to the `default` hash so the Dokploy UI stays truthful. `default` must keep `+@all`: AOF replay of `MULTI/EXEC` runs as `default`, and a disabled `default` makes Redis drop those transactions on restart (`NOPERM ... AOF-loading-client`, seen 21/10/2026). Edit the ACL file, test it on a side instance (`redis-server --port 6391 --aclfile ...`), then Reload.

### RabbitMQ specifics

Image `rabbitmq:3.13-management`, volume on `/var/lib/rabbitmq`, `RABBITMQ_NODENAME=rabbit@localhost`, file mounts `/etc/rabbitmq/enabled_plugins` (management, prometheus, stomp) and `/etc/rabbitmq/conf.d/90-notesweb.conf` (watermark 192MiB). `guest` is deleted. In 3.13 `rabbitmqctl add_user` does not read a piped password: read it with `read -rs` and pass it as an argument.

## Change a password

- Postgres: `psql -U postgres -d NoteApp` then `\password notes_app`; update `DB_PASSWORD`; redeploy `Notes_Server`.
- Redis: append/replace the user's line with `#$(printf '%s' "$P" | sha256sum | cut -d' ' -f1)`, test on a side instance, Reload `notes-redis`, then update `Notes_Server`.
- RabbitMQ: `rabbitmqctl change_password notes_mq "$P"` (from `read -rs`), then update `Notes_Server`.
- Never put a password on a command line you keep, in chat, PRs or screenshots.

## Rotation

- Every 3–6 months, and immediately after any suspected exposure.
- Rotate one service at a time: new credential, redeploy, verify, remove the old one.
- Record the date in the infra Notion page, never the value.

## Restore test (monthly)

1. Pick the latest encrypted backup in S3 and confirm its timestamp is inside the daily window.
2. Restore into a throwaway Postgres container, never into production.
3. Check row counts of `users`, `notes`, `todo`, `message_inbox` and that the app boots against it.
4. Delete the throwaway container and record the result and the time taken (this is the measured RTO).

## Exposure response

If a secret ever lands in git (file, commit message, PR, log), treat it as exposed even after it is removed: rotate it everywhere production uses it, then redeploy. Rewriting history is optional and destructive; rotation is what removes the risk. Rotating `JWT_SECRET` logs every user out.

## Guardrails

- Keep `.env` gitignored; commit only `.env.example`.
- Enable GitHub secret scanning and push protection on the repository (Settings → Code security).
- `.github/workflows/security.yml` runs Trivy (dependencies, secrets, misconfig) on PRs and weekly; it is report-only until its first run is clean.
- `.github/dependabot.yml` opens weekly update PRs for Maven, GitHub Actions and Docker.
