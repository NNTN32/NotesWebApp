# PR 3: RabbitMQ note updates

No production deployment, schema migration, broker deletion or merge is performed by this PR. This supersedes the Kafka transport section of the PR 2 runbook; its backup, auth, notification and at-least-once limitations still apply.

## Runtime

`PUT /notes/update/{noteID}` keeps its authenticated ownership check and JSON body. HTTP 202 (`Note update accepted`) follows the PostgreSQL outbox transaction, even while RabbitMQ is unavailable. Failure to commit returns an error, not acceptance. `NoteUpdatePublisher` preserves the existing event JSON, UUID and per-note advisory acceptance lock.

Canonical outbox rows use `NOTE_UPDATE` (published over RabbitMQ), exchange `notes.note-updates.v1`, and note UUID as routing key. A dedicated durable quorum queue binds all note keys on that topic exchange. Persistent messages require mandatory routing and publisher confirms. Earlier pending rows for a note block later publishes for that note, including across relay processes.

The listener uses manual ACK, prefetch 1 and single active consumer. It also checks earlier outbox commands against committed inbox/dead-letter records before updating: broker redelivery ordering alone is not the ordering contract. A blocked later edit is deferred through a delayed outbox copy before ACK, without consuming its failure budget; this lets its predecessor retry instead of getting stuck behind a repeatedly requeued message. Business mutation, inbox identity `note-update:<eventId>` (unchanged from PR 2), and notification outbox commit together before ACK. Lost ACK redelivery cannot mutate the note twice.

Processing failures first commit a delayed retry outbox copy, then ACK. A stable transport ID deduplicates a lost-ACK retry write. On the fifth recorded failure, PostgreSQL retains the original payload and inserts a stable-ID outbox copy to `notes.events.dlq`, then the listener ACKs. A failed retention transaction leaves the original unacknowledged/requeued. A terminal redelivery is ACKed without applying a stale edit after newer edits. Malformed payloads use a SHA-256 identity and follow the same retention path. No retry deletes the original outbox or DLQ record.

Canonical NOTE_UPDATE rows alone define acceptance order; RABBIT retry copies keep the original business ID without becoming new predecessors. One active note consumer limits throughput and infrastructure outages can delay the note queue. Partition into measured per-note shards when load requires concurrency. Reminders use a different queue/listener. Real browser receipt still requires a separate client acknowledgement/reconnect feature; STOMP outbox publication means server handoff only.

Kafka client/config/listeners, Compose service and CI bootstrap variables are removed. The legacy staging replay scheduler is removed; `staging_notes` data/model/repository remain for reconciliation. Historical ops scripts and old runbooks describe the old deployment, not a PR 3 rollout. Redis auth/create/media flows are unchanged.

## Future cutover: preserve existing work

1. Back up PostgreSQL and Kafka storage; rehearse recovery. Inventory Kafka topic/partition offsets, consumer lag, `staging_notes`, outbox rows and database dead letters. Pause note producers at the API/ingress boundary. Keep the existing Kafka broker and its storage available.
2. Let the PR 2 application relay/consumer drain accepted Kafka work. Zero consumer lag alone does not prove business success: inspect retained Kafka dead letters and inbox/business state. If published work cannot be drained or reconciled, stop the cutover. Pre-PR-2 records without stable event IDs need manual conflict/dedup reconciliation. Never replay old edits over later edits blindly.
3. Stop the old application (including its relay and consumers), then review/apply `db/messaging.sql` for the additive ordering index. Review/run **`db/migrate-note-updates-to-rabbit.sql` manually** for any remaining PENDING rows. It converts only PENDING Kafka note-command rows in place; UUID, payload, routing key, sequence, due time and attempts stay intact. Published history and dead letters remain untouched. Unexpected/malformed envelopes abort the transaction; resolve them explicitly rather than dropping rows. Re-running a successful conversion is safe. New startup fails closed while PENDING Kafka rows exist, so skipping the conversion cannot silently strand accepted commands.
4. Start the Rabbit-only version, verify confirmed publishing, inbox progress, oldest outbox age, retry/DLQ counts and final note versions. Resume producers after reconciliation. Do not run old/new consumers or relays concurrently. Do not remove Kafka storage until a separately approved retirement and retention period.

Before rollback, stop new producers/consumers and reconcile Rabbit/outbox/inbox work. Restoring an old binary or re-running the historical ops script is not a safe automatic rollback. No automatic reverse conversion or bulk DLQ replay is provided. Replay of terminal edits requires reviewing later edits and intentionally resolving the terminal marker while preserving its audit record; ordinary redelivery deliberately cannot bypass it.

## Regression checks

Existing PR 2 tests remain for outbox rollback, uncertain publish, concurrent relays/inbox, real Rabbit connection loss, mandatory returns, Redis pending reclaim and auth-result TTL. Kafka-specific tests are replaced by Rabbit note tests.

Added coverage: HTTP ownership/202/error behavior; manual ACK after business handling; failed-retention requeue; malformed/missing identity; blocked-order retry budget; terminal redelivery; real JPA rollback of note/inbox/notification; duplicate delivery after lost ACK; durable acceptance without a broker; an actual PostgreSQL outbox -> confirmed Rabbit publish -> consumer -> note/inbox/notification pipeline with a transient serialization failure; and atomic/idempotent legacy cutover that preserves published history and rejects invalid rows.

Use only disposable PostgreSQL, Redis and RabbitMQ with the `MESSAGING_TEST_*` variables listed in the PR 2 runbook:

```sh
bash notesWeb/mvnw -B -f notesWeb/pom.xml test '-Dtest=!NotesWebApplicationTests'
```

CI starts the built image and runs the application-context/OpenAPI smoke test without any Kafka container or environment variable. Production migration, broker failover/host loss, live FE behavior and load benchmarks are outside these checks.
