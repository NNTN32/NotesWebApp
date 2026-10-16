package com.example.notesWeb.notes.media;

import com.example.notesWeb.notes.media.infrastructure.SecurityUploadService;
import com.example.notesWeb.notes.media.messaging.MediaRedisProducer;
import com.example.notesWeb.auth.domain.User;
import com.example.notesWeb.notes.media.domain.NoteMedia;
import com.example.notesWeb.notes.domain.Notes;
import com.example.notesWeb.notes.media.persistence.MediaRepo;
import com.example.notesWeb.notes.persistence.NotesRepo;
import com.example.notesWeb.notes.media.application.TaskMediaService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OwnershipTest {
    @Mock RedisTemplate<String, Object> redis;
    @Test void foreignUploadIsRejectedBeforeExternalUploadOrEnqueue() {
        NotesRepo notes = mock(NotesRepo.class);
        SecurityUploadService cloud = mock(SecurityUploadService.class);
        MediaRedisProducer producer = new MediaRedisProducer(redis, cloud, notes);
        UUID noteId = UUID.randomUUID(), attacker = UUID.randomUUID();
        assertThatThrownBy(() -> producer.sendMediaRequest("mallory", attacker, noteId,
                new MockMultipartFile("file", new byte[]{1}))).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(cloud, redis);
    }
    @Test void knownNoteAndMediaIdsDoNotAuthorizeForeignDeletion() {
        MediaRepo repo = mock(MediaRepo.class);
        TaskMediaService service = new TaskMediaService(repo);
        User owner = new User(); owner.setId(UUID.randomUUID());
        Notes note = new Notes(); note.setId(UUID.randomUUID()); note.setUser(owner);
        NoteMedia media = new NoteMedia(); media.setId(UUID.randomUUID()); media.setNotes(note);
        when(repo.findById(media.getId())).thenReturn(Optional.of(media));
        assertThatThrownBy(() -> service.deleteMedia(note.getId(), media.getId(), UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
        verify(repo, never()).delete(any());
        service.deleteMedia(note.getId(), media.getId(), owner.getId());
        verify(repo).delete(media);
    }
}
