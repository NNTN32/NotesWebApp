package com.example.notesWeb.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FailureLedger {
    private final JdbcTemplate jdbc;
    private final Outbox outbox;
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dead(String key, String source, String payload, String reason) {
        jdbc.update("""
                INSERT INTO message_dead_letter(message_key,source,payload,reason) VALUES (?,?,?,?)
                ON CONFLICT DO NOTHING
                """, key, source, payload, reason);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean redisFailure(String key, String stream, String recordId) {
        int attempt = nextAttempt(key);
        if (attempt < 5) return false;
        // Reference only: never duplicate authentication credentials into the database/DLQ.
        dead(key, stream, recordId, "Redis processing exhausted retries; original retained in stream");
        return true;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rabbitFailure(String key, String payload, String reason) {
        int attempt = nextAttempt(key);
        if (attempt >= 5) {
            dead(key, "rabbit", payload, reason);
            outbox.append(UUID.nameUUIDFromBytes(("dead:"+key).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    "RABBIT", "notes.events.v2", "dead", payload, Instant.now());
        } else {
            // New transport envelope; business eventId inside the payload stays unchanged.
            outbox.append(UUID.randomUUID(), "RABBIT", "notes.events.v2", "reminder.ready", payload,
                    Instant.now().plusSeconds(Math.min(60, 1L << attempt)));
        }
    }
    private int nextAttempt(String key) {
        return jdbc.queryForObject("""
                INSERT INTO message_attempt(message_key,attempts) VALUES (?,1)
                ON CONFLICT (message_key) DO UPDATE SET attempts=message_attempt.attempts+1 RETURNING attempts
                """, Integer.class, key);
    }
}
