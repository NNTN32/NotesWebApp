# Backend working rules

- Java 21, Spring Boot **3.5.16**. App lives in `notesWeb/`; package ownership: [feature modules](notesWeb/docs/feature-modules.md).
- Preserve ownership/security checks, JWT/STOMP boundaries, RabbitMQ publisher confirms, transactional outbox/inbox, ACK/retry/DLQ and Redis Streams contracts. No Kafka.
- Tests: `node --test scripts/ci/*.test.mjs`; in `notesWeb/`, `bash mvnw -B test '-Dtest=!*IntegrationTest,!NotesWebApplicationTests'`. Integration/context tests require disposable PostgreSQL/Redis/RabbitMQ and CI fixture variables from `.github/workflows/CI.yml`; never use production `.env` or delete production volumes.
- Release through a reviewed PR and green CI. [CD runbook](notesWeb/docs/ci-cd.md) is authoritative: current merged main → immutable digest → Dokploy API on Lightsail Malaysia → runtime/readiness/public smoke + observation → latest promotion.
- Deployment target is fixed in `scripts/ci/deploy.mjs`; no OrbStack fallback and no mutable latest/webhook deployment. ProductBE credentials are main-only; never commit keys, environment exports or webhook URLs.
- Failed release remains failed after rollback. Preserve data/settings; ambiguous or concurrent deployments require reconciliation, not another blind POST. Keep rollback artifacts and encrypted off-VM backups.
- Production changes require user authorization and passed release gates. Do not upgrade AWS Free Plan or expose DB/queue/admin ports as a deployment shortcut.
