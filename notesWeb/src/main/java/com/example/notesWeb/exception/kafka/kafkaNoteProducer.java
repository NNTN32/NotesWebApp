package com.example.notesWeb.exception.kafka;

import com.example.notesWeb.dtos.NoteDto.NoteUpdateEvent;
import com.example.notesWeb.messaging.Outbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class kafkaNoteProducer {
    private final Outbox outbox;
    private final ObjectMapper json;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Transactional
    public void sendNoteUpdate(NoteUpdateEvent event) {
        try {
            // Serialize command acceptance per note, including across application instances.
            jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",event.getNoteID().toString());
            outbox.append(event.getEventId(), "KAFKA", "note-updates", event.getNoteID().toString(), json.writeValueAsString(event), Instant.now());
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalArgumentException("Invalid note command");
        }
    }
}
