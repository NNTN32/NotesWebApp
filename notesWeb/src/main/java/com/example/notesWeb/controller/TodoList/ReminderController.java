package com.example.notesWeb.controller.TodoList;

import com.example.notesWeb.config.jwtProvider;
import com.example.notesWeb.model.User;
import com.example.notesWeb.model.todoLists.ListTodo;
import com.example.notesWeb.repository.UserRepo;
import com.example.notesWeb.service.todoLists.ReminderService;
import com.example.notesWeb.service.todoLists.SchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.text.DateFormat;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reminder")
public class ReminderController {
    private final ReminderService reminderService;
    private final SchedulerService schedulerService;
    private final jwtProvider jwtProvider;
    private final UserRepo userRepo;

    @PostMapping("/set-time/{idListTodo}")
    public ResponseEntity<?> setDeadlineReminder(
            @RequestHeader("Authorization") String authorHeader,
            @PathVariable UUID idListTodo,
            @RequestParam String time,
            @RequestParam Integer reminder
    ) {
        try {
            if (authorHeader == null || !authorHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Authorization header is missing or invalid.");
            }

            String token = authorHeader.substring(7);

            // Check token expiration
            if (jwtProvider.isTokenExpired(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Token expired. Please login again.");
            }

            // Get username from JWT
            String username = jwtProvider.getUserFromJwt(token);
            User user = userRepo.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found!"));

            // Callback logic service
            ListTodo updated = reminderService.setDeadlineByPresent(idListTodo, time, reminder, username);

            return ResponseEntity.ok(updated);

        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}
