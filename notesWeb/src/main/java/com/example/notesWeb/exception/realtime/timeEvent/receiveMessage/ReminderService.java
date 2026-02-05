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
    //Inject dependency
    private final TodoRepo todoRepo;
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

            ListTodo todo = todoRepo.findByIdWithUser(idListTodo)
                    .orElseThrow(() -> new FailedException("Todo not found with id: " + idListTodo));

            if (!todo.getUser().getUsername().equals(username)) {
                throw new FailedException("Unauthorized: This todo does not belong to the current user.");
            }

            todo.setDeadlineTime(dt);

            long reminderMinutes = reminder.toMinutes();
            todo.setReminderTime(reminderMinutes);

            //Time trigger
            Instant trigger = dt.minusMinutes(reminderMinutes).atZone(ZoneId.systemDefault()).toInstant();

            //prevent on duplicate sent reminder
            todo.setTriggerAt(trigger);
            todo.setState(State.PENDING);
            todo.setReminded(false);

            todoRepo.save(todo);

            //Calculate delay from present -> trigger
            long delayMs = Duration.between(Instant.now(), trigger).toMillis();

            //1 minute publish MQ soon <=> > 1 minute Scheduler will poll later & save into DB
            if (delayMs <= 60_000) {
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
