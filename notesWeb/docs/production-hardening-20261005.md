# Production checklist — evidence 05/10/2026

- PostgreSQL: `notes_observer` can SELECT; INSERT/UPDATE/DELETE denied, no superuser/role/db creation. Default read-only transactions. TablePlus `NoteAppProduct` changed to localhost:15433, production tag, Safe Mode 2, Ask every time; Test and Connect passed.
- Redis: `notes_app` restricted to application key prefixes/command categories, `notes_observer` metadata plus read selectors for notes/media (no auth stream payload), `notes_admin` separate. Passwords generated locally and stored in root-only /var/lib/notesweb-hardening/20261005/credentials.json and owner's ~/.config/notesweb-production/credentials.json (0600). Anonymous default user disabled. ACL file persisted on Redis data volume; AOF everysec enabled. Swarm endpoint changed DNSRR → VIP to avoid cached task addresses after replacement. Production authenticated workers verified after restart.
- RabbitMQ: `guest` removed; dedicated notes_app (no admin tag), notes_monitor (monitoring only, no queue read/write), notes_admin. Internal broker ports only. Node name preserved with persistent data and startup hostname resolution. STOMP plugin enabled by persistent Dokploy command. Reminder consumer count 1.
- Networking: PostgreSQL, Redis, Kafka and RabbitMQ have no host/ingress published ports. Host `ss` no longer listens on 5433, 5672, 15672 or 61613. BE 8081 / Dokploy 3000 remain as existing routes. SSH tunnel binds to localhost only: PG15433, Redis16379, Rabbit Management15673. Docker policies don't rely on UFW (not installed).
- Persistence: notesweb-kafka-data → /tmp/kafka-logs (UID1000), notesweb-rabbitmq-data → /var/lib/rabbitmq. Single VM placement, stop-first updates. Dokploy mount/command/env/endpoint records reconciled with live Swarm configuration. Original specs and metadata saved to root-only rollback directory; metadata changes used the server-side database since no API key was provisioned.
- Backups: Kafka paused crash-consistent snapshot; Rabbit stop_app snapshot while BE stopped. Data archives with SHA256 stored under /var/lib/notesweb-hardening/20261005. Restored to separate internal staging network, verified existing topic note-updates and Rabbit reminder queues. Backups are on the same VM; this is not off-site disaster recovery or multi-node HA.
- Staging: fresh PostgreSQL/Redis, restored Kafka/RabbitMQ, generated synthetic user/note/todo, same application jar as production. Redis create → row commit → pending0; Kafka update → row commit → committed group; Rabbit reminder → DB SENT → unacked0. Simulated failed Redis consumer PEL ownership, 10s+ idle, restarted BE → claimed/persisted/ACK. Kafka queued while BE stopped → processed after restart. No production messages or DB fixtures were written. Auth/media, DB outage, exhaustive retry and multi-node failover were not tested.
- BE: one production JVM, no extra worker app; owns API + Redis/Kafka/Rabbit consumers + scheduler. Stop-first replaced start-first because host port prevented start-first convergence.
- Redis client fix: isolated branch from main at 56b631c, commit e8db05b5001af882cd0336952af33dbd28f963c9. Java21 Docker build and RedisConfigTest 2/2 pass. Bind RedisProperties and pass username/password/database into Lettuce configuration. Draft PR https://github.com/NNTN32/NotesWebApp/pull/47 pending merge before switching to a future main image.
- Runtime image: nntn/notes-app:be-acl-e8db05b@sha256:2f7e00bd1df494c4086d7ca951fd52d265f76c4073f38e0556071e513104f6b4. Pushed Docker Hub and pinned in Dokploy. This hardening rollout was a manual tested image promotion, not a GitHub Actions run.
- Smoke: production Redis PING authenticated and anonymous NOAUTH, observer write ACL DRYRUN denied, 3 blocking Redis worker clients, Rabbit consumer 1, Kafka consumer assigned, broker volumes present. Public /v3/api-docs HTTP200.

## Connections after hardening

Run `bash notesWeb/ops/connect-production.sh` from /Users/nhannguyen/Desktop/NotesWebApp. Keep terminal open. Re-resolve task bridge addresses after redeploy; don't hardcode them.

| Tool | Host/port | Account |
|---|---|---|
| TablePlus PostgreSQL | 127.0.0.1:15433 / NoteApp | notes_observer |
| TablePlus Redis | 127.0.0.1:16379 / db0 | notes_observer |
| RabbitMQ Management | http://127.0.0.1:15673 | notes_monitor (observe), notes_admin (admin only as needed) |
| Kafka CLI | inside broker localhost:9093 | existing internal CLI path |

Redis TablePlus Test + Connect passed as notes_observer; server CLIENT LIST confirms the observer client. Password saved in macOS Keychain with user permission. PostgreSQL Ask every time worked.

RabbitMQ notes_monitor: Management UI login and API overview HTTP200 verified via localhost:15673 SSH tunnel.
