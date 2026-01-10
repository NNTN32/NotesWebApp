package com.example.notesWeb.service.todoLists;


import com.example.notesWeb.model.User;
import com.example.notesWeb.model.todoLists.ListTodo;
import com.example.notesWeb.repository.UserRepo;
import com.example.notesWeb.repository.todoRepo.TodoRepo;
import com.example.notesWeb.service.FailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderService {
    private final SchedulerService schedulerService;
    private final TodoRepo todoRepo;
    private final UserRepo userRepo;

    // Set deadline theo giờ cụ thể (HH:mm) + username từ token
    public ListTodo setDeadlineByPresent(UUID idListTodo, String hhmm, Duration reminder, String username) {

        String[] parts = hhmm.split(":");
        int hh = Integer.parseInt(parts[0]);
        int mm = Integer.parseInt(parts[1]);

        LocalDate today = LocalDate.now();

        //Throw business exception for setup time maybe invalid
        try {
            LocalTime setTime = LocalTime.parse(hhmm, DateTimeFormatter.ofPattern("HH:mm"));
            LocalDateTime dt = today.atTime(setTime);

            // Nếu thời gian đã qua, tự động dời sang ngày mai
            if (dt.isBefore(LocalDateTime.now())) {
                dt = today.plusDays(1).atTime(setTime);
            }

            // Cập nhật todo theo user (đảm bảo todo thuộc user đó)
            ListTodo todo = todoRepo.findById(idListTodo)
                    .orElseThrow(() -> new FailedException("Todo not found with id: " + idListTodo));

            if (!todo.getUser().getUsername().equals(username)) {
                throw new FailedException("Unauthorized: This todo does not belong to the current user.");
            }

            todo.setDeadlineTime(dt);
            todo.setReminderTime(reminder);
            todo.setReminded(false);

            ListTodo saved = todoRepo.save(todo);

            // Schedule reminder realtime
            schedulerService.scheduleReminder(saved);

            log.info("Set reminder for '{}' at {} by user '{}'", todo.getHeading(), dt, username);
            return saved;
        }catch (DateTimeException e) {
            throw new FailedException("Invalid time format, expected HH:mm");
        }

    }

    //Logic handle update deadline vs reminder time for schedule
    public ListTodo updatedTime (UUID idListTodo, LocalDateTime newDeadline, Duration newReminder, String username) {
        ListTodo todo = todoRepo.findByIdWithUser(idListTodo)
                .orElseThrow(() -> new FailedException("Todo not found with id: " + idListTodo));

        if (!todo.getUser().getUsername().equals(username)) {
            throw new FailedException("Unauthorized: This todo does not belong to the current user.");
        }

        schedulerService.updateReminderTime(idListTodo, newDeadline, newReminder);
        log.info("Updated deadline & reminder for '{}' by user '{}'", todo.getHeading(), username);

        return todoRepo.findByIdWithUser(idListTodo).orElseThrow();
    }
}
