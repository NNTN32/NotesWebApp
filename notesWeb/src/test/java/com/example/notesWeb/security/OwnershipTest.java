package com.example.notesWeb.security;

import com.example.notesWeb.exception.redis.mediaNoteRedis.*;
import com.example.notesWeb.model.*;
import com.example.notesWeb.model.takeNotes.*;
import com.example.notesWeb.repository.noteRepo.*;
import com.example.notesWeb.service.takeNotes.TaskMediaService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OwnershipTest {
    @Test void foreignUploadIsRejectedBeforeExternalUploadOrEnqueue() {
        NotesRepo notes = mock(NotesRepo.class);
        SecurityUploadService cloud = mock(SecurityUploadService.class);
        MediaRedisProducer producer = new MediaRedisProducer(cloud, notes);
        UUID noteId = UUID.randomUUID(), attacker = UUID.randomUUID();
        assertThatThrownBy(() -> producer.sendMediaRequest("mallory", attacker, noteId,
                new MockMultipartFile("file", new byte[]{1}))).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(cloud);
    }
    @Test void knownNoteAndMediaIdsDoNotAuthorizeForeignDeletion() {
        MediaRepo repo = mock(MediaRepo.class);
        TaskMediaService service = new TaskMediaService(mock(NotesRepo.class), repo);
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
