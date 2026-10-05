# TablePlus — NotesRedis Production

Verified 05/10/2026: Test + Connect Redis 7.4.11; server confirms notes_observer client.

| Setting | Value |
|---|---|
| Host | 127.0.0.1 |
| Port | 16379 |
| Database | 0 |
| User | notes_observer |
| Password | Store in keychain, explicitly authorized |
| Tag | production |
| Over SSH | Off; external SSH tunnel |

Open the tunnel on the Mac and keep the terminal open:

```bash
bash /Users/nhannguyen/Desktop/NotesWebApp/notesWeb/ops/connect-production.sh
```

The script resolves current container IPs and binds PostgreSQL:15433, Redis:16379 and RabbitMQ Management:15673 to localhost. Reopen after task IP changes. No DB/broker host ports are published.

Observer may inspect metadata and read notes:*, note:*, media:*; writes and auth/login/session payload are denied. Anonymous default user is disabled. Never put passwords in docs.

See [production hardening evidence](production-hardening-20261005.md) and [Notion runbook](https://app.notion.com/p/3f084e0b99dd813d927dd749ed5f6661).
