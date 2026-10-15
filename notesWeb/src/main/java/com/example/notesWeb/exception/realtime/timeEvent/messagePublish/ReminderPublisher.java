package com.example.notesWeb.exception.realtime.timeEvent.messagePublish;

import com.example.notesWeb.messaging.Outbox;
import com.example.notesWeb.dtos.TodoListDto.notificatePopup.ReminderDelay;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class ReminderPublisher {
    private final Outbox outbox;
    private final ObjectMapper json;
    public static UUID eventId(UUID todoId, Instant trigger) {
        return UUID.nameUUIDFromBytes(("reminder:"+todoId+":"+trigger).getBytes(StandardCharsets.UTF_8));
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void schedule(UUID todoId, Instant trigger) {
        try {
            outbox.append(eventId(todoId,trigger), "RABBIT", "notes.events.v2", "reminder.ready",
                    json.writeValueAsString(ReminderDelay.builder().todoID(todoId).trigger(trigger).build()), trigger);
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalArgumentException("Invalid reminder");
        }
    }
}
