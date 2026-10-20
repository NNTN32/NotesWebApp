package com.example.notesWeb.todos.reminder;

import com.example.notesWeb.auth.domain.User;
import com.example.notesWeb.shared.error.FailedException;
import com.example.notesWeb.todos.domain.ListTodo;
import com.example.notesWeb.todos.domain.State;
import com.example.notesWeb.todos.persistence.TodoRepo;
import com.example.notesWeb.todos.reminder.application.ReminderService;
import com.example.notesWeb.todos.reminder.messaging.ReminderPublisher;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReminderServiceTest {
    TodoRepo repo = mock(TodoRepo.class);
    ReminderPublisher publisher = mock(ReminderPublisher.class);
    ReminderService service = new ReminderService(repo, publisher);

    ListTodo todo(State state) {
        User owner = new User(); owner.setId(UUID.randomUUID()); owner.setUsername("alice");
        ListTodo t = new ListTodo(); t.setIdList(UUID.randomUUID()); t.setUser(owner); t.setState(state);
        when(repo.findLockedById(t.getIdList())).thenReturn(Optional.of(t));
        return t;
    }

    @Test void completedTodoIsNotReopenedOrRescheduled() {
        ListTodo t = todo(State.CORRECT);
        assertThatThrownBy(() -> service.setDeadlineByPresent(t.getIdList(), "10:00", Duration.ofMinutes(10), "UTC", "alice"))
                .isInstanceOf(FailedException.class);
        assertThat(t.getState()).isEqualTo(State.CORRECT);
        verify(repo, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test void openTodoIsScheduled() {
        ListTodo t = todo(State.FAIL);
        service.setDeadlineByPresent(t.getIdList(), "10:00", Duration.ofMinutes(10), "UTC", "alice");
        assertThat(t.getState()).isEqualTo(State.PENDING);
        verify(repo).save(t);
        verify(publisher).schedule(eq(t.getIdList()), any());
    }
}
