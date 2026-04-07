package com.example.notesWeb.controller.TodoList;

import com.example.notesWeb.config.jwtProvider;
import com.example.notesWeb.model.User;
import com.example.notesWeb.model.todoLists.ListTodo;
import com.example.notesWeb.repository.UserRepo;
import com.example.notesWeb.exception.realtime.timeEvent.receiveMessage.ReminderService;
import com.example.notesWeb.exception.realtime.timeEvent.receiveMessage.ReminderScheduler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reminder")
public class ReminderController {
    private final ReminderService reminderService;
    private final ReminderScheduler reminderScheduler;
    private final jwtProvider jwtProvider;
    private final UserRepo userRepo;

    @Operation(summary = "User set deadline time todo list",
               parameters = @Parameter(name = "X-Timezone", description = "User's time zone", in = ParameterIn.HEADER))
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/set-time/{idListTodo}")
    public ResponseEntity<?> setDeadlineReminder(
            @RequestHeader(value = "X-TimeZone", required = false, defaultValue = "UTC") String timezone,
//            @RequestHeader("Authorization") String authorHeader,
            Authentication authentication,
            @PathVariable UUID idListTodo,
            @RequestParam String time,
            @RequestParam String reminder
    ) {
        try {
            if (authentication == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Authorization header is missing or invalid.");
            }

//            String token = authorHeader.substring(7);
//
//            // Check token expiration
//            if (jwtProvider.isTokenExpired(token)) {
//                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
//                        .body("Token expired. Please login again.");
//            }

            // Get username from JWT
            String username = authentication.getName();
            User user = userRepo.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found!"));

            //Convert Duration
            Duration duration = Duration.parse(reminder);

            // Callback logic service
            ListTodo updated = reminderService.setDeadlineByPresent(idListTodo, time, duration, timezone, username);

            return ResponseEntity.ok(updated);

        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body("Wrong type input timezone");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

}
