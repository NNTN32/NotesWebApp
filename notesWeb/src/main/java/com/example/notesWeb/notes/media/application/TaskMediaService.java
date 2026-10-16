package com.example.notesWeb.notes.media.application;

import com.example.notesWeb.notes.media.domain.NoteMedia;
import com.example.notesWeb.notes.media.persistence.MediaRepo;
import com.example.notesWeb.shared.error.FailedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskMediaService {
    private final MediaRepo mediaRepo;

    //Logic handle delete Media of notes
    @org.springframework.transaction.annotation.Transactional
    public void deleteMedia(UUID noteId, UUID mediaID, UUID userId){
        NoteMedia noteMedia = mediaRepo.findById(mediaID)
                .orElseThrow(() -> new FailedException("File haven't uploaded yet! " + mediaID));

        if(!noteMedia.getNotes().getId().equals(noteId)
                || !noteMedia.getNotes().getUser().getId().equals(userId)){
            throw new org.springframework.security.access.AccessDeniedException("Media access denied");
        }
        mediaRepo.delete(noteMedia);
    }
}
