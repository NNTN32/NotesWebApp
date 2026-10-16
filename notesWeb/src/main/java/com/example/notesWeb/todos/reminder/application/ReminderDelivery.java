package com.example.notesWeb.todos.reminder.application;

import com.example.notesWeb.platform.messaging.Inbox;
import com.example.notesWeb.platform.messaging.Outbox;
import com.example.notesWeb.todos.reminder.contract.ReminderDelay;
import com.example.notesWeb.todos.reminder.contract.ReminderMessage;
import com.example.notesWeb.todos.reminder.messaging.ReminderPublisher;
import com.example.notesWeb.todos.persistence.TodoRepo;
import com.example.notesWeb.todos.domain.State;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class ReminderDelivery {
    private final TodoRepo todos;
    private final Inbox inbox;
    private final Outbox outbox;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    @Transactional(rollbackFor=Exception.class) public void deliver(ReminderDelay event) throws Exception {
        if (event.getTodoID()==null) throw new IllegalArgumentException("Missing reminder id");
        var todo=todos.findLockedById(event.getTodoID()).orElse(null);
        if (todo==null || Boolean.TRUE.equals(todo.getReminded()) || todo.getState()==State.CORRECT) return;
        Instant trigger=todo.getTriggerAt();
        if (trigger==null) return;
        if (event.getTrigger()!=null && !event.getTrigger().equals(trigger)) return;
        if (trigger.isAfter(Instant.now())) throw new IllegalStateException("Reminder not due");
        if (todo.getState()!=State.PENDING && todo.getState()!=State.QUEUE) return;
        UUID id=ReminderPublisher.eventId(todo.getIdList(),trigger);
        if (!inbox.claim("reminder:"+id)) return;
        String payload=json.writeValueAsString(ReminderMessage.builder().todoID(todo.getIdList())
                .title("Reminder").message("It's time to: "+todo.getHeading()).build());
        jdbc.update("INSERT INTO reminder_notification(id,username,payload) VALUES (?,?,?) ON CONFLICT DO NOTHING",id,todo.getUser().getUsername(),payload);
        outbox.append(UUID.nameUUIDFromBytes(("notify:"+id).getBytes(java.nio.charset.StandardCharsets.UTF_8)),"STOMP",todo.getUser().getUsername(),"/queue/reminder",payload,Instant.now());
        todo.setState(State.SENT); todo.setReminded(true); todos.saveAndFlush(todo);
    }
}
