# BE IDE warning review — 2026-10-16

The screenshot showed 94 warnings. The live window initially showed 96, including a warning for a Kafka source file already removed from main. After **Java: Clean Java Language Server Workspace**, Cursor rebuilt the project and reported **95 warnings, zero errors**. All 95 entries were read in the Problems table, with file paths and line numbers. After this change, the same IDE reports **44 warnings, zero errors**. No diagnostic severity or suppression setting was changed.

## Classification and disposition

| Category | Before | After | Priority and action |
|---|---:|---:|---|
| Spring Boot patch available | 1 | 0 | Update 3.5.3 → 3.5.16; use the matching Boot Maven plugin and BOM-managed libraries. |
| Spring Boot OSS support ended | 1 | 1 | Important follow-up: plan a separate Boot 4 migration. A patch upgrade does not restore OSS support. Keep the warning visible. |
| Unused imports | 40 | 0 | Remove imports confirmed unused by the Java compiler/IDE. No behavior changes. |
| Null annotation inference | 4 | 1 | Remove the unnecessary JWT claim callback helper. Review and fix actual missing-expiry handling; leave the unrelated JPA method-reference inference warning. |
| Field injection → constructor injection | 24 | 24 | Maintainability follow-up. No demonstrated runtime failure; avoid changing every constructor and its callers in this bug-fix PR. |
| Unused fields, locals or private methods | 9 | 7 | Delete two dead upload helpers and their commented caller. Keep the remaining cleanup separate. |
| Raw/unchecked generic types | 14 | 10 | Validate and type the live Cloudinary boundary. Delete the unchecked upload helper. Remaining warnings are Redis test mocks and generic varargs calls. |
| Potential null dereference | 2 | 1 | Remove the dead retry helper containing `lastException.getMessage()`. The remaining warning is in a test after an AssertJ `isNotNull()` assertion. |
| **Total** | **95** | **44** | **51 warnings removed.** |

No P0/P1 application exploit or production outage was established from these diagnostics. The concrete robustness defects and dependency drift below are fixed; the support lifecycle remains a separate material risk.

## Changes that affect behavior or the runtime image

- **JWT missing expiry:** a correctly signed token without `exp` was rejected by the main authentication validator, but calling `isTokenExpired`/`getRemainingTime` directly could dereference null. These helpers now report expired/zero remaining time. They still verify the signature; no parsing or authentication check is bypassed.
- **Cloudinary boundary:** replace raw maps with a validated `Map<String,String>` containing only nonblank string `secure_url`/`resource_type`. Null, incomplete or incorrectly typed responses fail before any Redis enqueue. The temporary file is removed on success and rejection. Cloudinary itself is mocked in tests.
- **Dead upload path:** `validateFileType` and `uploadRetry` had no active callers; the active path is `MediaNoteController → MediaRedisProducer → SecurityUploadService`. Delete the dead implementation, including its potential NPE and swallowed interrupt, rather than maintain a second upload implementation.
- **Dependency alignment:** update the Boot parent to 3.5.16 and inherit its plugin version. Remove explicit overrides for Jackson, Lettuce, PostgreSQL, Reactor Netty and Commons Codec so their versions follow the BOM. Observed dependency tree: Jackson core/databind 2.21.4, Lettuce 6.6.0.RELEASE, PostgreSQL 42.7.11 and Reactor Netty 1.2.18.
- **Test dependency isolation:** put `spring-boot-starter-test` in test scope and remove the unused JUnit import from production Redis configuration. Test tooling is no longer a compile/runtime dependency of the application.

Spring's [3.5.16 release announcement](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/) identifies this as the last OSS 3.5 release. Moving to Boot 4 changes Spring/Jackson integration and needs its own compatibility work. This change does not claim to eliminate all dependency vulnerabilities.

## Remaining warnings by file

Paths below are relative to `notesWeb/`.

| File | Count | Follow-up |
|---|---:|---|
| `pom.xml` | 1 | Boot support lifecycle |
| `src/main/java/com/example/notesWeb/controller/AuthController.java` | 4 | Constructor injection |
| `src/main/java/com/example/notesWeb/controller/Notes/NoteController.java` | 9 | 7 injections, unused service field and local variable |
| `src/main/java/com/example/notesWeb/controller/TodoList/ListController.java` | 4 | Constructor injection |
| `src/main/java/com/example/notesWeb/controller/TodoList/ReminderController.java` | 3 | 2 unused fields and a local variable |
| `src/main/java/com/example/notesWeb/exception/RedisStreamConsume.java` | 2 | Injection and generic varargs |
| `src/main/java/com/example/notesWeb/exception/redis/authRedis/AuthRedisConsumer.java` | 1 | Constructor injection |
| `src/main/java/com/example/notesWeb/exception/redis/authRedis/AuthRedisProducer.java` | 1 | Constructor injection |
| `src/main/java/com/example/notesWeb/exception/redis/mediaNoteRedis/MediaRedisProducer.java` | 1 | Constructor injection |
| `src/main/java/com/example/notesWeb/exception/redis/noteRedis/NoteRedisProducer.java` | 2 | Injection and unused constant |
| `src/main/java/com/example/notesWeb/service/AuthService.java` | 4 | Constructor injection |
| `src/main/java/com/example/notesWeb/service/takeNotes/TaskMediaService.java` | 1 | Unused repository field |
| `src/main/java/com/example/notesWeb/service/takeNotes/TaskNoteService.java` | 1 | Null annotation inference on `NoteMedia::getUrl` |
| `src/test/java/com/example/notesWeb/messaging/DeliverySafetyTest.java` | 2 | Mockito generic conversions |
| `src/test/java/com/example/notesWeb/messaging/MessagingIntegrationTest.java` | 2 | AssertJ null inference and Redis varargs |
| `src/test/java/com/example/notesWeb/security/AuthServiceSecurityTest.java` | 3 | Mockito generic conversions |
| `src/test/java/com/example/notesWeb/security/CredentialLoggingTest.java` | 3 | Mockito generic conversions |

When removing unused local variables in controllers, preserve the repository lookup and `orElseThrow`: their validation side effect still matters even when the variable is never read. Do not delete the lookup merely to clear a warning.

## Validation and limits

New regressions cover missing JWT expiry, validated Cloudinary output, null/malformed output rejection, temporary-file cleanup, no Redis enqueue after invalid metadata, and reading existing Redis session/stream string wire formats after the dependency update. Local validation passed: 44 unit tests plus 22 disposable PostgreSQL/Redis/RabbitMQ integration/context tests (66 total, zero failures/errors/skips). The executable jar packaged successfully; inspection confirmed no JUnit, Mockito, Spring Test, AssertJ or Awaitility libraries in `BOOT-INF/lib`. The existing PR CI additionally exercises the built image and OpenAPI endpoint.

No database schema, broker topology, consumer acknowledgement behavior, production configuration or production data is changed. Real Cloudinary uploads and a Boot 4 migration are outside this PR. No merge or production deployment is authorized for this change.
