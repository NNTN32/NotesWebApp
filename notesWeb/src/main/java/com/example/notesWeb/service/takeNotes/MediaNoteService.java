package com.example.notesWeb.service.takeNotes;

import com.example.notesWeb.model.takeNotes.MediaType;
import com.example.notesWeb.model.takeNotes.NoteMedia;
import com.example.notesWeb.model.takeNotes.Notes;
import com.example.notesWeb.repository.IdGenerateRepo;
import com.example.notesWeb.repository.noteRepo.MediaRepo;
import com.example.notesWeb.repository.noteRepo.NotesRepo;
import com.example.notesWeb.service.FailedException;
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
    private final com.example.notesWeb.messaging.Inbox inbox;
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
