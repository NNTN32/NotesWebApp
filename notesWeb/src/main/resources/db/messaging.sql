CREATE TABLE IF NOT EXISTS message_outbox (
 sequence_id BIGSERIAL UNIQUE, id UUID PRIMARY KEY, kind VARCHAR(20) NOT NULL, destination TEXT NOT NULL, routing_key TEXT NOT NULL,
 payload TEXT NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'PENDING', attempts INTEGER NOT NULL DEFAULT 0,
 available_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), published_at TIMESTAMPTZ,
 last_error VARCHAR(200)
);
CREATE INDEX IF NOT EXISTS message_outbox_due ON message_outbox(status, available_at);
CREATE INDEX IF NOT EXISTS message_outbox_note_order ON message_outbox(destination,routing_key,sequence_id) WHERE kind='NOTE_UPDATE';
CREATE TABLE IF NOT EXISTS message_inbox (
 message_key VARCHAR(300) PRIMARY KEY, processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS message_attempt (
 message_key VARCHAR(300) PRIMARY KEY, attempts INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS message_dead_letter (
 message_key VARCHAR(300) PRIMARY KEY, source VARCHAR(200) NOT NULL, payload TEXT NOT NULL,
 reason VARCHAR(200) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS reminder_notification (
 id UUID PRIMARY KEY, username VARCHAR(255) NOT NULL, payload TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
