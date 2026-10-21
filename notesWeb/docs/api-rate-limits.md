# API rate limits

All budgets use atomic Redis token buckets, shared across replicas and restarts. A bucket starts full and refills continuously at capacity/window. These are burst capacities plus refill rates, not fixed calendar windows.

| Request | Identity | Burst capacity | Refill |
| --- | --- | --- | --- |
| POST /api/auth/login | client IP | 60 | 60/minute |
| POST /api/auth/login | username + client IP | 10 | 10/5 minutes |
| POST /api/auth/register | client IP | 20 | 20/hour |
| Other API requests, including auth polling and refresh | authenticated user UUID, otherwise client IP | 300 | 300/minute |

OPTIONS and health checks bypass application limits. Login/register do not consume the polling budget. Accounts behind the same NAT have independent ordinary API budgets; account throttling never locks a victim account globally. Do not automatically retry non-idempotent writes. On 429 honor Retry-After with jitter; do not repeatedly submit login while polling a queued login session.

Cloudflare Free has one edge rate rule and permits path matching only. It covers exact /api/auth/login and /api/auth/register paths (all methods/zone hosts), 30 requests per client IP per 10 seconds, block for 10 seconds. It excludes /api/auth/login/result/* by exact matching. This is burst protection; precise limits are enforced before expensive work at the origin. Edge counters can overshoot and differ per data center. BIC exception remains API-only. WAF and DDoS are not skipped.

## Trusted client IP

Production sets `APP_RATE_LIMIT_EDGE_SECRET` to a random private value (at least 32 bytes). A Cloudflare HTTP request-header transform **sets**, never adds, `X-Notes-Edge-Key` on the API hostname. Only when that value matches in constant time does the application use CF-Connecting-IP. Otherwise it uses the socket peer, ignoring X-Forwarded-For and all claimed client identities. Do not log or reflect the edge proof, or allow public traffic to override the transform. Keep the origin private behind the tunnel. Cloudflare account administrators remain trusted.

Development needs no edge secret. Never configure a generic “trust all proxies” mode to solve NAT issues. When rotating the edge proof, update the rule and backend together in a maintenance window; a mismatch temporarily groups public anonymous traffic under the tunnel peer.

## Redis and failure behavior

Keys use `auth:rate-limit:<budget>:<SHA256(identity)>`, contain no password, bearer token or raw username/IP, and expire once the bucket could fully refill. Redis ACL needs TIME, HMGET, HSET, PEXPIRE, EVAL/EVALSHA for this prefix. Shared Redis outage returns 503 + Retry-After: 5 before new work is accepted; it never reports an unperformed write as successful. Health endpoints remain available. No database migration is needed.

## Rollout and rollback

Back up the CapRover app settings and Cloudflare rules before rollout. Run unit tests and ApiRateLimitIntegrationTest against disposable Redis, then CI, a real allowed login, bounded 429 checks and recovery. Never use real user credentials to test brute force. Check CORS exposes Retry-After and bad credentials/forged tokens/origins still fail.

Emergency disable: `APP_RATE_LIMIT_ENABLED=false` (requires application restart), or revert to the previously pinned image. Disable only the new http_ratelimit rule for edge rollback. Remove only the new edge-proof header transform after the old backend is restored. Do not restore an old database or broker volume. Preserve configuration snapshots privately because the header transform contains a secret.

The limits reduce brute force and signup abuse; they do not eliminate distributed low-rate attacks. Large shared-IP populations may need tuning from measured traffic. No paid Cloudflare plan is required.
