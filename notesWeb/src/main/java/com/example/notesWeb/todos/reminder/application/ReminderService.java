package com.example.notesWeb.todos.reminder.application;


import com.example.notesWeb.todos.reminder.messaging.ReminderPublisher;
import com.example.notesWeb.todos.domain.ListTodo;
import com.example.notesWeb.todos.domain.State;
import com.example.notesWeb.todos.persistence.TodoRepo;
import com.example.notesWeb.shared.error.FailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    @org.springframework.transaction.annotation.Transactional
    public ListTodo setDeadlineByPresent(UUID idListTodo, String time, Duration reminder,String userTimezone, String username) {

        //Throw business exception for setup time maybe invalid
        try {
            //Convert type string time input into zoneID first
            ZoneId zoneId;
            try {
                zoneId = ZoneId.of(userTimezone != null ? userTimezone : "UTC");
            }catch (DateTimeException e){
                throw new FailedException("Invalid time zone!" + userTimezone);
            }

            //Get the current time for that time zone
            ZonedDateTime userZone = ZonedDateTime.now(zoneId).truncatedTo(ChronoUnit.MINUTES);

            //Parse input time zone by user
            LocalTime setTime;
            try {
                setTime = LocalTime.parse(time).truncatedTo(ChronoUnit.MINUTES);
            }catch (DateTimeException e) {
                throw new FailedException("The time format is incorrect; please use HH:mm (e.g., 16:50)!");
            }

            //Combine into a specific time
            ZonedDateTime dt = userZone.with(setTime);

            // If the time has passed, it will automatically be moved to tomorrow.
            if (dt.isBefore(userZone)) {
                dt = dt.plusDays(1);
            }

            //Time trigger
            Instant deadlineInstant = dt.toInstant();
            Instant trigger = deadlineInstant.minus(reminder);


            ListTodo todo = todoRepo.findLockedById(idListTodo)
                    .orElseThrow(() -> new FailedException("Todo not found with id: " + idListTodo));

            if (!todo.getUser().getUsername().equals(username)) {
                throw new FailedException("Unauthorized: This todo does not belong to the current user.");
            }

            // A completed todo must not be silently reopened by scheduling a reminder.
            if (todo.getState() == State.CORRECT) {
                throw new FailedException("This todo is marked as CORRECT and cannot be rescheduled!");
            }

            todo.setDeadlineTime(deadlineInstant);
            todo.setReminderTime(reminder.toMinutes());
            //prevent on duplicate sent reminder
            todo.setTriggerAt(trigger);
            todo.setReminded(false);
            todo.setState(State.PENDING);

            todoRepo.save(todo);

            reminderPublisher.schedule(todo.getIdList(), trigger);

            log.info("Set reminder for '{}' at {} by user '{}'", todo.getIdList(), trigger);
            return todo;
        }catch (DateTimeException e) {
            throw new FailedException("Invalid time format, expected HH:mm");
        }

    }
}
