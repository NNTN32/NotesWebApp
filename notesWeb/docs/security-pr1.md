# Security PR 1

Scope: close cross-user access and token-boundary defects in the current backend before changing broker delivery or deploying. No database schema migration, queue deletion, data cleanup, production access, or deployment is included.

## Changes

- Public registration accepts a dedicated DTO without a role; the service always assigns USER.
- All note, media, todo and reminder routes require authentication, including newly added routes. Media upload authorizes the note owner before Cloudinary or enqueue; media delete checks the authenticated owner. Todo list/delete reject forged owner IDs and use the principal.
- AuthResponse implements Principal so reminder controllers get the authenticated username rather than Object.toString().
- API and STOMP authentication accepts unexpired Access tokens only. Refresh/logout validate unexpired Rotation tokens before reading session/grace state. Expired claims are no longer returned as trusted claims.
- CORS and SockJS use an exact allowlist configured by APP_ALLOWED_ORIGINS (default localhost:5173 and https://nhannotes.id.vn). STOMP CONNECT requires a native Authorization: Bearer <access token> header. Clients can subscribe only to /user/queue/reminder and /user/queue/note-updates; raw broker destinations and client SEND are denied. Authentication headers are stripped before relay; expiration is checked on subsequent inbound commands. Clients must reconnect with a fresh access token on expiration. This does not proactively terminate an idle socket at token expiration.
- Authentication producers/consumers and shared stream error paths no longer log credential payloads or arbitrary exception details. Anonymous health requests no longer receive details.

## Compatibility and follow-up

The old async login/polling contract remains for this PR. Credentials still transit the legacy auth Redis Stream; removing them requires the planned auth contract migration. New STOMP connections must authenticate after obtaining an access token; anonymous login-result subscriptions are deliberately denied. Clients relying on login-result WebSocket delivery must use the existing HTTP result polling until the auth migration. Test the actual frontend before merging.

This PR does not drain or delete historical Redis Stream entries or logs. Existing credential exposure, session revocation, existing ADMIN users and controlled historical-data retention require an operator-reviewed follow-up before deployment. Do not indiscriminately flush Redis: it contains sessions and pending work.

Refresh rotation atomicity, login result delivery atomicity, rate limiting before enqueue, durable auth events, queue ACK/retry, outbox, reminder recovery and Kafka removal belong to subsequent PRs. Session/grace storage behavior is otherwise preserved.

## Regression verification

Run from notesWeb:

    bash ./mvnw -B test '-Dtest=!*IntegrationTest,!NotesWebApplicationTests' -Dsurefire.failIfNoSpecifiedTests=false

Tests cover forged owner IDs, unauthorized note read/update, service-level media denial before upload/deletion, successful own-resource operations, forced USER registration, access/rotation and expiration boundaries, refresh rejection before session access, credential logging, STOMP destination restrictions and CORS allow/deny cases. The HTTP tests use the real Spring Security filter chain and mocked business/infrastructure dependencies. They do not contact production. GitHub PR CI additionally starts disposable PostgreSQL/Redis/Kafka/RabbitMQ and runs the application context smoke test on Java 21.
