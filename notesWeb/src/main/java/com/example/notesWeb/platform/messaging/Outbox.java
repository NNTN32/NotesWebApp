package com.example.notesWeb.platform.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class Outbox {
    private final JdbcTemplate jdbc;
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(UUID id, String kind, String destination, String routingKey, String payload, Instant due) {
        jdbc.update("""
                INSERT INTO message_outbox(id,kind,destination,routing_key,payload,available_at)
                VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING
                """, id, kind, destination, routingKey, payload, Timestamp.from(due));
    }
}
