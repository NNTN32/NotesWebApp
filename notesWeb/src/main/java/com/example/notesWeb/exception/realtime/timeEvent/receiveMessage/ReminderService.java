package com.example.notesWeb.exception.realtime.timeEvent.receiveMessage;


import com.example.notesWeb.exception.realtime.timeEvent.messagePublish.ReminderPublisher;
import com.example.notesWeb.model.todoLists.ListTodo;
import com.example.notesWeb.model.todoLists.State;
import com.example.notesWeb.repository.UserRepo;
import com.example.notesWeb.repository.todoRepo.TodoRepo;
import com.example.notesWeb.service.FailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderService {
    private final TodoRepo todoRepo;
    private final UserRepo userRepo;
    private final RabbitTemplate rabbitTemplate;
    private final ReminderPublisher reminderPublisher;

    // Set a specific time (HH:mm) + username from the token.
    public ListTodo setDeadlineByPresent(UUID idListTodo, String hhmm, Duration reminder, String username) {

        //Throw business exception for setup time maybe invalid
        try {
//            LocalTime setTime = LocalTime.parse(hhmm, DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime setTime = LocalTime.parse(hhmm);
            LocalDateTime dt = LocalDateTime.of(LocalDate.now(), setTime);

            // If the time has passed, it will automatically be moved to tomorrow.
            if (dt.isBefore(LocalDateTime.now())) {
                dt = dt.plusDays(1);
            }

            // Update todos by user (ensure the todo belongs to that user)
            ListTodo todo = todoRepo.findByIdWithUser(idListTodo)
                    .orElseThrow(() -> new FailedException("Todo not found with id: " + idListTodo));

            if (!todo.getUser().getUsername().equals(username)) {
                throw new FailedException("Unauthorized: This todo does not belong to the current user.");
            }

            todo.setDeadlineTime(dt);

            long reminderMinutes = reminder.toMinutes();
            todo.setReminderTime(reminderMinutes);
            Instant trigger = dt.minusMinutes(reminderMinutes).atZone(ZoneId.systemDefault()).toInstant();

            todo.setTriggerAt(trigger);
            todo.setState(State.PENDING);
            todo.setReminded(false);

            todoRepo.save(todo);

            long delayMs = Duration.between(Instant.now(), trigger).toMillis();

            if (delayMs <= 60_000) { //1 minute to fire MQ.
                reminderPublisher.publishDelay(todo.getIdList(), delayMs);
                log.info("Publish reminder {} to MQ (delay {}ms)", todo.getIdList(), delayMs);
            } else {
                log.info("Reminder {} scheduled in DB only (delay {}ms)", todo.getIdList(), delayMs);
            }

            log.info("Set reminder for '{}' at {} by user '{}'", todo.getIdList(), trigger);
            return todo;
        }catch (DateTimeException e) {
            throw new FailedException("Invalid time format, expected HH:mm");
        }

    }
}
