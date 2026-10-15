# Reliable messaging PR 2

Stacked on `codex/be-security-pr1` (BE PR #50). No merge, production connection, schema change on production or deployment is part of this PR.

## Delivery contract

At-least-once transport, with transactional business deduplication. This prevents silent discards in the covered failure paths; it is not an exactly-once network or browser-delivery claim. Durability assumes PostgreSQL and broker storage survive and have backups. Single-node volumes do not provide high availability or protection from loss of the host/disk.

- PostgreSQL outbox writes join the business transaction (`MANDATORY`). A relay locks one due row using `FOR UPDATE SKIP LOCKED`, sends persistent Rabbit messages with mandatory routing + correlated confirms, and only then marks the row published. Timeouts, nacks, returns and transport outages retain the original payload with capped backoff. There is no automatic deletion/expiry of pending outbox rows.
- Kafka is retained temporarily. API 202 for an update now follows durable command insertion, not an unobserved asynchronous send. A stable eventId survives relay retries. Per-note advisory locks serialize acceptance, and an earlier pending command blocks later commands for that note while allowing unrelated notes through. Broker send futures are observed; consumers commit their inbox marker and note update together before ACK. Five failed deliveries retain the envelope in the database dead-letter table before advancing the Kafka offset. Kafka removal remains a later PR.
- Inbox markers have a unique key and commit in the same JPA/JDBC transaction as note/media/reminder changes. Rollback removes the marker too. A redelivery after a lost ACK therefore skips a previously committed business mutation.
- Rabbit reminder failures are moved to delayed PostgreSQL outbox retry rows before ACK. After five failures the original payload is retained in `message_dead_letter` and an outbox row forwards a copy to quorum queue `notes.events.dlq`. If retention fails, the original delivery is NACKed with requeue. Invalid JSON is handled inside the raw-message listener, not discarded by pre-listener conversion. The new ready quorum queue has no implicit delivery-limit discard.
- Redis ACK is owned by the common consumer, after the handler commits or a terminal failure reference commits. Exceptions are no longer swallowed by note/media handlers; in-consumer rate limits no longer discard work. Pending entries are paginated and reclaimed on every poll (minimum idle 30 seconds), including after restart. Workers are bounded and shut down with the application.
- Redis terminal failures retain only stream name and original record ID in PostgreSQL, never credentials. The original stream entry is not deleted or trimmed; use XRANGE to inspect/replay under controlled access. Keep consumer groups and original streams intact.
- Authentication stores SUCCESS/FAIL polling results, TTL and a completion marker atomically in Redis, with a short processing lease. Completed requests are not re-authenticated when their original record redelivers within the marker window (24 hours), even if the result was consumed. Infrastructure failures remain pending/retryable. This preserves the legacy HTTP login contract and makes terminal credential failures visible to polling. Password-free auth events and atomic session/refresh rotation remain the auth-migration PR; PostgreSQL, credential verification and Redis session issuance are not one distributed transaction.
- Reminder schedule changes and outbox insertion now commit together; due time stays in PostgreSQL rather than a TTL delay queue. Scheduler recovery includes overdue PENDING/QUEUE rows. Existing legacy Rabbit queues remain and are still consumed. A canonical todo+trigger identity deduplicates legacy/new envelopes, stale re-armed schedules are skipped, and a row lock serializes reminder delivery/re-arm. `reminder_notification` persists notification data alongside the state change. STOMP handoff is retried through outbox, but PUBLISHED means handed to the server messaging channel, not received/read by a browser. A client unread-notification API/reconnect UI belongs to the notification feature PR.

## Schema and storage preparation (before any future deployment)

Review and apply `src/main/resources/db/messaging.sql` using the deployment migration process. It only creates additive messaging tables/indexes. Runtime defaults to **not** applying DDL and fails startup if the required tables/sequence column are missing. `APP_MESSAGING_INITIALIZE_SCHEMA=true` is for disposable CI/dev only. A full Flyway baseline of existing application tables is not introduced here.

Compose adds named volumes for RabbitMQ, Redis and Kafka. Redis uses AOF with `appendfsync always`. These settings apply only when this Compose is explicitly used in the future. Do not attach an empty volume over a running production broker data directory, run a production `down --volumes`, reset consumer groups or clear Redis. Copy/verify existing broker storage, take backups and rehearse restore before switching deployment storage. Existing hosted/Dokploy configuration is unchanged by this PR.

Before cutover, pause producers and stop old consumers, inventory Kafka offsets, Redis pending records and `staging_notes`, and reconcile deliveries whose business write may have succeeded before old code lost its ACK. Historical deliveries do not have inbox markers. Blindly replaying them can duplicate legacy creates or overwrite newer notes. The legacy staging scheduler is disabled by default; rows are preserved. Its opt-in flag is for controlled reconciliation only, not an automatic replay recommendation. Do not run old and new consumers concurrently during cutover.

## Inspect and replay

Run read-only monitoring queries with an operator connection:

```sql
SELECT kind,status,count(*),min(created_at) FROM message_outbox GROUP BY kind,status;
SELECT count(*),min(created_at) FROM message_dead_letter;
SELECT source,count(*) FROM message_dead_letter GROUP BY source;
SELECT count(*) FROM reminder_notification;
```

Alert on oldest pending age, sustained relay failures, new dead letters, Redis pending age/count and reminder lag. The tables retain payloads/reference data for investigation; they must not be exposed through public HTTP or copied into application logs.

Outbox transport errors retry automatically. For DLQ recovery, first fix and verify the cause, identify the original event, compare current domain state, and replay through its original processing route with the **same business identity**, not a new ID. Inspect the retained dead-letter row and routing before creating a targeted outbox retry; reset only that event's attempt counter if another bounded retry cycle is intended. Keep the audit/dead-letter original until recovery is verified. Replaying a Kafka/staging update after later edits requires version/conflict reconciliation; do not bulk-replay it. Redis failures reference the retained stream entry; authentication retries need a fresh user login rather than exporting old passwords. No public replay endpoint or bulk-delete script is included.

## Regression verification

Unit tests cover ACK ordering, failure-retention failure, malformed payloads, lost ACK propagation and Rabbit nack/mandatory return handling. Integration tests run against explicitly configured disposable PostgreSQL/Redis/RabbitMQ: transaction rollback, real JPA+JDBC atomicity, concurrent inbox/relay workers, stable IDs across application-context restart, publish uncertainty, same-note ordering, durable DLQ, physical Rabbit connection loss before ACK, Redis pending reclaim and atomic auth-result TTL.

From `notesWeb`, unit tests:

    bash ./mvnw -B test '-Dtest=!*IntegrationTest,!NotesWebApplicationTests'

Integration tests require `MESSAGING_TEST_DB_URL`, `MESSAGING_TEST_DB_USERNAME`, `MESSAGING_TEST_DB_PASSWORD`, `MESSAGING_TEST_RABBIT_HOST`, `MESSAGING_TEST_RABBIT_PORT`, `MESSAGING_TEST_RABBIT_USERNAME`, `MESSAGING_TEST_RABBIT_PASSWORD`, `MESSAGING_TEST_REDIS_PORT`. Use disposable services only. Tests create unique schemas/queues/stream keys and clean those resources; missing explicit test infrastructure fails the integration suite. CI provides its own containers, starts the built application, runs OpenAPI/context smoke and all integration suites, then destroys only disposable CI storage.

Real Kafka broker crash/failover, disk/host failure, restore from backup, browser notification receipt, live production migration and concurrent auth-session rotation are outside the evidence from these regression tests. Keep this PR draft until rollout prerequisites are reviewed.
