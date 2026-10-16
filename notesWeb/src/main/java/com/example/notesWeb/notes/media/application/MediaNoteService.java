package com.example.notesWeb.notes.media.application;

import com.example.notesWeb.notes.media.domain.MediaType;
import com.example.notesWeb.notes.media.domain.NoteMedia;
import com.example.notesWeb.notes.domain.Notes;
import com.example.notesWeb.shared.id.IdGenerateRepo;
import com.example.notesWeb.notes.media.persistence.MediaRepo;
import com.example.notesWeb.notes.persistence.NotesRepo;
import com.example.notesWeb.shared.error.FailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaNoteService {
    private final com.example.notesWeb.platform.messaging.Inbox inbox;
    private final NotesRepo notesRepo;
    private final MediaRepo mediaRepo;
    private final IdGenerateRepo idGenerateRepo;

    @Transactional
    public void saveOnce(String eventKey, UUID postId, String url, String type) {
        if (inbox.claim(eventKey)) saveMediaMetadata(postId, url, type);
    }

    //Logic handle about upload file on notes like photo, video, audio,...
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveMediaMetadata (UUID postID, String url, String resourceType) {
        Notes notes = notesRepo.findById(postID)
                .orElseThrow(() -> new FailedException("Post doesn't exist!"));

        MediaType type = MediaType.RAW;
        try {
            type = MediaType.valueOf(resourceType.toUpperCase());
        }catch (Exception e) {
            log.warn("Unknown resource type: {}", resourceType);
        }

        NoteMedia noteMedia = new NoteMedia();
        noteMedia.setId(idGenerateRepo.nextId());
        noteMedia.setUrl(url);
        noteMedia.setType(type);
        noteMedia.setNotes(notes);

        mediaRepo.save(noteMedia);
    }
}
