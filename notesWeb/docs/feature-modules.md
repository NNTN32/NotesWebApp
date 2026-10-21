# Backend package ownership

Keep Java 21 / Spring Boot **3.5.16** for this refactor. This is one deployable application and one database, organized by feature; no Maven submodules or new framework are needed.

Under `com.example.notesWeb`:

| Package | Responsibility |
| --- | --- |
| `auth` | HTTP auth contract, users, sessions, JWT, HTTP/STOMP security and rate limiting; auth Redis producer/consumer/results |
| `notes` | Note API/application/domain/persistence/contracts and Redis/Rabbit note delivery |
| `notes.media` | Note media ownership, Cloudinary adapter, media API/application/persistence and Redis metadata delivery |
| `todos` | Todo API/application/domain/persistence and contracts |
| `todos.reminder` | Reminder API, scheduling, delivery, notifications and Rabbit envelopes/consumers |
| `platform.messaging` | Generic outbox/inbox, confirmed transport, retry/failure ledger, relay and Rabbit topology |
| `platform.redis` | Redis connection/serialization, stream groups and recoverable consumer base |
| `platform.web`, `platform.websocket` | OpenAPI/CORS and WebSocket transport configuration |
| `bootstrap` | Environment and application-ready consumer wiring |
| `shared` | Shared exceptions and ID generation |

Start a feature change in its own package: HTTP handling in `api`, use cases/transactions in `application`, JPA types in `domain`, repositories in `persistence`, request/event shapes in `contract`, broker adapters in `messaging`. Media stays part of notes; reminders stay part of todos.

## Boundaries and existing coupling

Features must not call a peer feature's controllers, application services or consumers. Generic messaging/Redis infrastructure and shared utilities do not import business features; bootstrap explicitly wires the workers. `FeatureStructureTest` enforces these rules without another dependency.

Existing typed auth/principal/security and user repository access remain available to notes/todos. The bidirectional JPA `User.notes` association remains, including its cascade and JSON behavior. This is intentional shared persistence coupling, not proof that the features are independently deployable or that every package dependency is acyclic. Introduce narrower cross-feature interfaces only when a real use case needs them; do not change ownership/cascade semantics just to move files.

## Compatibility constraints

- Component, entity and repository discovery remain below the same application root. JPQL's fully qualified todo state enum follows its new package; SQL table/column names, entity names and relationships do not change.
- Keep event IDs, JSON fields, stream/group names, Rabbit destinations/routing, ACK order, retry/DLQ and transaction boundaries unchanged. Rabbit commands use raw JSON message bodies, not Java class-name envelopes. Redis login/create/media fields and session markers are strings; the configured serializer stays unchanged.
- Retain legacy `StagedUpdate`/`StagingRepo` and migration SQL for historical persisted data. Moving packages does not authorize deleting old tables, queue payloads or history.
- Do not rename bean classes while moving them: existing bean names, qualifiers and proxy behavior remain stable.
- No POM, environment, SQL migration or production topology changes are part of this refactor. Boot 4 remains separate work.

## Regression and release gate

`PersistedMessageContractTest` reads pre-refactor note/reminder payloads and Redis string bytes. The application context tests check all feature controllers/repositories/consumers, entity discovery, the reminder JPQL and public paths. Existing security, outbox/idempotency, lost ACK, retry/DLQ and pending-reclaim tests remain mandatory.

Before merge/deploy: run unit and disposable infrastructure integration tests, require green PR/main CI, restore fresh production backups on an isolated network, compare the current and candidate OpenAPI contract, replay old JSON envelopes and test the exact release image. Canary auth/429, note create/update and reminders; verify existing business fingerprints and backlog after fixture cleanup.

Rollback swaps the previous pinned application image/configuration, preserving current database and broker data. A package move must not require data rollback. Existing JPA associations and real Cloudinary behavior still need their own broader design/testing work.
