package com.example.notesWeb.notes.messaging;

import com.example.notesWeb.platform.messaging.Outbox;

import com.example.notesWeb.notes.contract.NoteUpdateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class NoteUpdatePublisher {
    private final Outbox outbox;
    private final ObjectMapper json;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Transactional
    public void sendNoteUpdate(NoteUpdateEvent event) {
        if (event.getEventId()==null || event.getNoteID()==null || event.getUserID()==null || event.getNoteRequest()==null
                || event.getUsername()==null || event.getUsername().isBlank())
            throw new IllegalArgumentException("Incomplete note command");
        try {
            // Serialize command acceptance per note, including across application instances.
            jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",event.getNoteID().toString());
            outbox.append(event.getEventId(), "NOTE_UPDATE", "notes.note-updates.v1", event.getNoteID().toString(), json.writeValueAsString(event), Instant.now());
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalArgumentException("Invalid note command");
        }
    }
}
