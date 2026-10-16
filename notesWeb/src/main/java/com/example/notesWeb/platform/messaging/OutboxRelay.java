package com.example.notesWeb.platform.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxRelay {
    private final JdbcTemplate jdbc;
    private final ConfirmedPublisher publisher;
    // One row/transaction: concurrent relays skip locked rows. Crash before commit => safe redelivery.
    @Transactional
    public boolean publishNext() {
        var rows = jdbc.queryForList("""
                SELECT * FROM message_outbox o WHERE status='PENDING' AND available_at <= now()
                AND (kind <> 'NOTE_UPDATE' OR NOT EXISTS (
                    SELECT 1 FROM message_outbox earlier WHERE earlier.kind='NOTE_UPDATE'
                    AND earlier.destination=o.destination AND earlier.routing_key=o.routing_key
                    AND earlier.status='PENDING' AND earlier.sequence_id < o.sequence_id
                ))
                ORDER BY available_at,sequence_id LIMIT 1 FOR UPDATE OF o SKIP LOCKED
                """);
        if (rows.isEmpty()) return false;
        var row = rows.getFirst();
        UUID id = (UUID) row.get("id");
        try {
            publisher.send(id, (String)row.get("kind"), (String)row.get("destination"),
                    (String)row.get("routing_key"), (String)row.get("payload"));
            jdbc.update("UPDATE message_outbox SET status='PUBLISHED',published_at=now(),last_error=NULL WHERE id=?", id);
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            // Retain indefinitely on transport outages. Operators can inspect/replay without losing payloads.
            jdbc.update("""
                    UPDATE message_outbox SET attempts=LEAST(attempts+1,1000000),
                    available_at=now()+ interval '1 second' * LEAST(60, power(2,LEAST(attempts,6))),
                    last_error='Publish failed or confirmation uncertain' WHERE id=?
                    """, id);
        }
        return true;
    }
}
