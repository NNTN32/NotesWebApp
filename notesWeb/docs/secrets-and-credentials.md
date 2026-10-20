# Secrets & credentials

Rule: local and production use the **same variable names**, different values. Values never go in git, chat, or PR text.

| Where | Source of values |
|---|---|
| Local | `notesWeb/.env` (copy from `.env.example`; gitignored) and the defaults in `application.yml` (`localhost`, `postgres`, `guest`) |
| Production | Dokploy environment of each service only |
| CI | GitHub secrets: `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, `DOKPLOY_API_KEY`. DB, Redis and Rabbit credentials are **not** in GitHub |

## Production variables (Dokploy, service `Notes_Server`)

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`: a dedicated `notes_app` role, never the superuser.
- `RABBIT_USERNAME`, `RABBIT_PASSWORD`: a dedicated `notes_mq` user, never `guest`.
- `SPRING_DATA_REDIS_USERNAME`, `SPRING_DATA_REDIS_PASSWORD`: Spring relaxed binding of `spring.data.redis.*`; `RedisConfig` already reads both, no code change.
- `JWT_SECRET`, `CLOUDINARY_API_SECRET`: unique to production.

## Create the dedicated identities (one time)

Generate passwords on the VM and store them in a password manager. Do not paste them into chat, PRs or docs.

```bash
PG=$(docker ps -qf name=postgres | head -1)
docker exec -i "$PG" psql -U <current-db-user> -d NoteApp <<'SQL'
CREATE ROLE notes_app LOGIN PASSWORD '<PASSWORD>';
ALTER DATABASE "NoteApp" OWNER TO notes_app;
DO $$ DECLARE r record; BEGIN
  FOR r IN SELECT tablename FROM pg_tables WHERE schemaname = 'public'
  LOOP EXECUTE format('ALTER TABLE public.%I OWNER TO notes_app', r.tablename); END LOOP;
END $$;
SQL

RB=$(docker ps -qf name=rabbitmq | head -1)
docker exec "$RB" rabbitmqctl add_user notes_mq '<PASSWORD>'
docker exec "$RB" rabbitmqctl set_permissions -p / notes_mq ".*" ".*" ".*"
```

Order: create the new identity, set the Dokploy variables, redeploy `Notes_Server`, verify (login, set reminder, upload media, worker lag), and only then remove the old role or user (`DROP ROLE`, `rabbitmqctl delete_user`). Changing Postgres or Rabbit initial-credential env on an existing data volume has no effect; always use SQL / `rabbitmqctl`.

The Postgres and Redis services are native Dokploy databases: change their password in the service's General tab, then redeploy, then update the matching variable on `Notes_Server`. A backup of the database comes first.

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
