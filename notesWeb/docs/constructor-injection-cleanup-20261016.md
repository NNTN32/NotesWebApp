# Constructor injection and remaining code warnings — 2026-10-16

Following PR #55, Cursor reported 44 warnings. This change removes **all 43 code warnings** without changing compiler/IDE severities or adding suppressions. The Problems table now shows **one warning, zero errors**: Spring Boot 3.5's OSS support lifecycle.

| Category | Before | After | Change |
|---|---:|---:|---|
| Field injection | 24 | 0 | Required dependencies are final constructor arguments; one unused injected service was removed. |
| Unused fields/locals | 7 | 0 | Remove unused dependencies, retain user lookups and `orElseThrow`, use the existing note stream constant. |
| Redis generic/varargs warnings | 10 | 0 | Typed Mockito `@Mock` fields; single-stream reads use `boundStreamOps` with the same consumer, count, blocking interval and read offset. |
| Null analysis inference | 2 | 0 | Use a lambda for media URL mapping and an explicit non-null assertion after Rabbit redelivery in the integration test. |
| Spring Boot OSS support | 1 | 1 | Visible follow-up; separate Boot 4 migration. |

## Security and delivery invariants

- Note creation and reminder scheduling still look up the authenticated user and reject a missing user before any enqueue/scheduling call. Removing an unread local variable does not remove that validation.
- Ownership checks for uploads, media deletion and note updates are unchanged. Registration still forces the server-owned role; invalid refresh tokens never read Redis sessions.
- Constructor injection includes `FailureLedger` in every Redis consumer and `AuthResults` in the auth consumer. Worker startup still happens after Spring creates the dependencies.
- Redis recovery, ACK-after-commit, durable failure retention, stream keys, consumer groups, rate limits and credential-safe logging are unchanged. Remove the ignored worker-count argument and unused WebSocket constructor argument; neither controlled runtime behavior.
- No schema, wire format, queue topology, API contract or production settings are changed by this cleanup.

## Verification

47 unit tests and 22 disposable PostgreSQL/Redis/RabbitMQ integration/context tests passed locally (69 total, no failures/errors/skips). Three new regressions prove missing users still block note creation/reminder scheduling and anonymous reminders fail before repository access. Existing ownership, role, rate-limit, JWT, credential logging, outbox/inbox, lost-ACK and Redis pending-reclaim regressions run with the real constructor signatures.

The release gate is green PR/main CI plus a backed-up rollout using a pinned image, with rollback to the previous pinned service image/configuration. Backups and restore checks must precede production changes. Do not restore an old database over newer writes when rolling back code.

## Separate Boot 4 migration

Do not hide the remaining lifecycle warning. Boot 3.5.16 is the [last OSS 3.5 release](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/). A separate migration should:

1. Select a currently supported Boot 4 minor and review the official [migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide).
2. Audit Spring Security, springdoc, Spring Data Redis and Jackson compatibility. Test existing Redis session/stream payloads before changing serializers or Jackson defaults.
3. Run the full security/messaging suite, image/OpenAPI smoke, restored-data staging tests and real authentication/429 checks.
4. Release with an explicit data-compatible rollback boundary. Treat any schema or serialized-data migration as separate reviewed work.

This cleanup and a patch-level release do not put Boot 3.5 back into OSS support. Real Cloudinary calls and a Boot 4 migration are not covered by these tests.
