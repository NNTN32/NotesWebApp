package com.example.notesWeb.security;

import com.example.notesWeb.config.jwtProvider;
import com.example.notesWeb.controller.Notes.*;
import com.example.notesWeb.controller.TodoList.ListController;
import com.example.notesWeb.dtos.AuthResponse;
import com.example.notesWeb.messaging.NoteUpdatePublisher;
import com.example.notesWeb.exception.redis.noteRedis.NoteRedisProducer;
import com.example.notesWeb.exception.redis.mediaNoteRedis.MediaRedisProducer;
import com.example.notesWeb.repository.*;
import com.example.notesWeb.repository.noteRepo.NotesRepo;
import com.example.notesWeb.service.takeNotes.*;
import com.example.notesWeb.service.todoLists.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@ContextConfiguration(classes = {ListController.class, MediaNoteController.class, NoteController.class,
        SecurityConfig.class, webCORSConfig.class})
class HttpSecurityRegressionTest {
    @Autowired MockMvc mvc;
    @MockitoBean jwtProvider tokens;
    @MockitoBean ApiRateLimiter rateLimiter;
    @MockitoBean TodoListService todos;
    @MockitoBean TaskListService tasks;
    @MockitoBean UserRepo users;
    @MockitoBean TaskMediaService media;
    @MockitoBean MediaRedisProducer uploads;
    @MockitoBean NoteUpdatePublisher noteUpdates;
    @MockitoBean CreateNoteService create;
    @MockitoBean TaskNoteService notes;
    @MockitoBean NoteRedisProducer noteQueue;
    @MockitoBean NotesRepo noteRepo;
    final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID(), resource = UUID.randomUUID();
    UsernamePasswordAuthenticationToken alice() {
        return new UsernamePasswordAuthenticationToken(AuthResponse.builder().id(alice).username("alice").build(),
                null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
    @Test void anonymousAndRefreshTokensCannotReadBusinessData() throws Exception {
        mvc.perform(get("/notes/listNotes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/todo/listUser/"+alice).header("Authorization", "Bearer rotation-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(tasks, notes);
    }
    @Test void forgedTodoOwnerIdsAreRejectedWithoutCallingService() throws Exception {
        mvc.perform(get("/todo/listUser/"+bob).with(authentication(alice()))).andExpect(status().isForbidden());
        mvc.perform(delete("/todo/delete/"+resource).param("idUser", bob.toString())
                .with(authentication(alice()))).andExpect(status().isForbidden());
        verifyNoInteractions(tasks);
    }
    @Test void ownTodoRequestsUsePrincipalIdentity() throws Exception {
        mvc.perform(get("/todo/listUser/"+alice).with(authentication(alice()))).andExpect(status().isOk());
        verify(tasks).getAllTodoList(alice);
        mvc.perform(delete("/todo/delete/"+resource).with(authentication(alice()))).andExpect(status().isOk());
        verify(tasks).deleteList(resource, alice);
    }
    @Test void mediaDeletionPassesPrincipalInsteadOfAnUntrustedUserId() throws Exception {
        doThrow(new org.springframework.security.access.AccessDeniedException("foreign media"))
                .when(media).deleteMedia(resource, resource, alice);
        mvc.perform(delete("/media/delete/"+resource).param("noteId", resource.toString())
                .param("userId", bob.toString()).with(authentication(alice())))
                .andExpect(status().isForbidden());
        verify(media).deleteMedia(resource, resource, alice);
    }
    @Test void foreignNoteCannotBeReadOrUpdated() throws Exception {
        when(notes.getListNoteID(resource, alice)).thenThrow(new com.example.notesWeb.service.FailedException("not found"));
        mvc.perform(get("/notes/"+resource).with(authentication(alice()))).andExpect(status().isNotFound());
        mvc.perform(put("/notes/update/"+resource).contentType("application/json").content("{\"title\":\"hijack\"}")
                .with(authentication(alice()))).andExpect(status().isForbidden());
        verifyNoInteractions(noteUpdates);
    }
    @Test void forgedOriginsCannotReadCredentialedResponses() throws Exception {
        mvc.perform(options("/api/auth/refresh").header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
        mvc.perform(options("/api/auth/refresh").header("Origin", "https://nhannotes.id.vn")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "https://nhannotes.id.vn"));
    }
    @Test void ownUpdateReturnsAcceptedOnlyAfterDurableCommandAcceptance() throws Exception {
        when(noteRepo.existsByIdAndUserId(resource,alice)).thenReturn(true);
        mvc.perform(put("/notes/update/"+resource).contentType("application/json").content("{\"title\":\"edit\"}")
                .with(authentication(alice()))).andExpect(status().isAccepted()).andExpect(content().string("Note update accepted"));
        verify(noteUpdates).sendNoteUpdate(argThat(e->e.getEventId()!=null && e.getUserID().equals(alice) && e.getUsername().equals("alice")));
        doThrow(new IllegalStateException("DB unavailable")).when(noteUpdates).sendNoteUpdate(any());
        mvc.perform(put("/notes/update/"+resource).contentType("application/json").content("{\"title\":\"edit\"}")
                .with(authentication(alice()))).andExpect(status().isInternalServerError());
    }
}
