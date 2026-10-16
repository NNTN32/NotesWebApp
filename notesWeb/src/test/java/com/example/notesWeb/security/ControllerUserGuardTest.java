package com.example.notesWeb.security;

import com.example.notesWeb.config.jwtProvider;
import com.example.notesWeb.controller.Notes.NoteController;
import com.example.notesWeb.controller.TodoList.ReminderController;
import com.example.notesWeb.dtos.NoteDto.NoteRequest;
import com.example.notesWeb.exception.realtime.timeEvent.receiveMessage.ReminderService;
import com.example.notesWeb.exception.redis.noteRedis.NoteRedisProducer;
import com.example.notesWeb.messaging.NoteUpdatePublisher;
import com.example.notesWeb.repository.UserRepo;
import com.example.notesWeb.repository.noteRepo.NotesRepo;
import com.example.notesWeb.service.takeNotes.TaskNoteService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ControllerUserGuardTest {
    @Test void missingUserStillStopsNoteCreationBeforeEnqueue() {
        var users = mock(UserRepo.class);
        var tokens = mock(jwtProvider.class);
        var queue = mock(NoteRedisProducer.class);
        var updates = mock(NoteUpdatePublisher.class);
        var controller = new NoteController(updates, tokens, users, mock(TaskNoteService.class), queue, mock(NotesRepo.class));
        when(tokens.getUserFromJwt("access")).thenReturn("deleted-user");
        assertThatThrownBy(() -> controller.create("Bearer access", new NoteRequest("content", "title")))
                .isInstanceOf(UsernameNotFoundException.class);
        verify(users).findByUsername("deleted-user");
        verifyNoInteractions(queue, updates);
    }

    @Test void missingUserStillStopsReminderScheduling() {
        var users = mock(UserRepo.class);
        var reminders = mock(ReminderService.class);
        var auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("deleted-user");
        var response = new ReminderController(reminders, users).setDeadlineReminder(
                "UTC", auth, UUID.randomUUID(), "2026-10-17T12:00:00", "PT5M");
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(users).findByUsername("deleted-user");
        verifyNoInteractions(reminders);
    }

    @Test void anonymousReminderStillFailsBeforeAnyRepositoryOrSchedulingWork() {
        var users = mock(UserRepo.class);
        var reminders = mock(ReminderService.class);
        var response = new ReminderController(reminders, users).setDeadlineReminder(
                "UTC", null, UUID.randomUUID(), "2026-10-17T12:00:00", "PT5M");
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(users, reminders);
    }
}
