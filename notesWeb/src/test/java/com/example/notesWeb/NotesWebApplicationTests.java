package com.example.notesWeb;

import com.example.notesWeb.auth.api.AuthController;
import com.example.notesWeb.auth.domain.User;
import com.example.notesWeb.auth.messaging.AuthRedisConsumer;
import com.example.notesWeb.auth.persistence.UserRepo;
import com.example.notesWeb.notes.api.NoteController;
import com.example.notesWeb.notes.domain.Notes;
import com.example.notesWeb.notes.domain.StagedUpdate;
import com.example.notesWeb.notes.media.api.MediaNoteController;
import com.example.notesWeb.notes.media.domain.NoteMedia;
import com.example.notesWeb.notes.media.persistence.MediaRepo;
import com.example.notesWeb.notes.messaging.NoteUpdateConsumer;
import com.example.notesWeb.notes.persistence.NotesRepo;
import com.example.notesWeb.notes.persistence.StagingRepo;
import com.example.notesWeb.todos.api.ListController;
import com.example.notesWeb.todos.domain.ListTodo;
import com.example.notesWeb.todos.domain.State;
import com.example.notesWeb.todos.persistence.TodoRepo;
import com.example.notesWeb.todos.reminder.api.ReminderController;
import com.example.notesWeb.todos.reminder.messaging.ReminderQueueConsume;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class NotesWebApplicationTests {
    @Autowired ApplicationContext context;
    @Autowired EntityManagerFactory entities;
    @Autowired RequestMappingHandlerMapping endpoints;

    @Test void contextFindsEveryFeatureControllerRepositoryAndConsumer() {
        for(Class<?> type:java.util.List.of(AuthController.class,NoteController.class,MediaNoteController.class,
                ListController.class,ReminderController.class,UserRepo.class,NotesRepo.class,MediaRepo.class,
                StagingRepo.class,TodoRepo.class,AuthRedisConsumer.class,NoteUpdateConsumer.class,ReminderQueueConsume.class))
            assertThat(context.getBeansOfType(type)).as(type.getName()).hasSize(1);
    }
    @Test void movedEntitiesAndFullyQualifiedReminderQueryRemainUsable() {
        var mappedTypes=entities.getMetamodel().getEntities().stream()
                .map(e->e.getJavaType()).collect(java.util.stream.Collectors.toSet());
        assertThat(mappedTypes).isEqualTo(java.util.Set.of(User.class,Notes.class,NoteMedia.class,StagedUpdate.class,ListTodo.class));
        assertThatCode(()->context.getBean(TodoRepo.class).findUpcomingReminder(State.PENDING,Instant.now())).doesNotThrowAnyException();
    }
    @Test void featureEndpointsKeepTheirExistingPublicPaths() {
        var paths=endpoints.getHandlerMethods().keySet().stream()
                .flatMap(mapping->mapping.getPatternValues().stream()).toList();
        assertThat(paths).contains("/api/auth/register","/api/auth/login","/api/auth/login/result/{sessionId}",
                "/notes/creates","/todo/createList","/reminder/set-time/{idListTodo}");
    }
}
