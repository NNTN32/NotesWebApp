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
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderService {
    //Inject dependency
    private final TodoRepo todoRepo;
    private final ReminderPublisher reminderPublisher;

    // Set a specific time (HH:mm) + username from the token.
    public ListTodo setDeadlineByPresent(UUID idListTodo, String time, Duration reminder,String userTimezone, String username) {

        //Throw business exception for setup time maybe invalid
        try {
            //Convert type string time input into zoneID first
            ZoneId zoneId = ZoneId.of(userTimezone != null ? userTimezone : "UTC");

            //Get the current time for that time zone
            ZonedDateTime userZone = ZonedDateTime.now(zoneId).truncatedTo(ChronoUnit.MINUTES);

            //Parse input time zone by user
            LocalTime setTime = LocalTime.parse(time).truncatedTo(ChronoUnit.MINUTES);

            //Combine into a specific time
            ZonedDateTime dt = userZone.with(setTime);

            // If the time has passed, it will automatically be moved to tomorrow.
            if (dt.isBefore(userZone)) {
                dt = dt.plusDays(1);
            }

            //Time trigger
            Instant deadlineInstant = dt.toInstant();
            Instant trigger = deadlineInstant.minus(reminder);


            ListTodo todo = todoRepo.findByIdWithUser(idListTodo)
                    .orElseThrow(() -> new FailedException("Todo not found with id: " + idListTodo));

            if (!todo.getUser().getUsername().equals(username)) {
                throw new FailedException("Unauthorized: This todo does not belong to the current user.");
            }

            todo.setDeadlineTime(deadlineInstant);
            todo.setReminderTime(reminder.toMinutes());
            //prevent on duplicate sent reminder
            todo.setTriggerAt(trigger);
            todo.setReminded(false);
            todo.setState(State.PENDING);

            todoRepo.save(todo);

            //Calculate delay from present -> trigger
            long delayMs = Duration.between(Instant.now(), trigger).toMillis();

            //1 minute publish MQ soon <=> > 2 minute Scheduler will poll later & save into DB
            if (delayMs > 0 && delayMs <= 120_000) {
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
