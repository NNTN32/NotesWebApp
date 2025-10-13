package com.example.notesWeb.service.takeNotes;

import com.example.notesWeb.model.takeNotes.NoteMedia;
import com.example.notesWeb.repository.noteRepo.MediaRepo;
import com.example.notesWeb.repository.noteRepo.NotesRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskMediaService {
    private final NotesRepo notesRepo;
    private final MediaRepo mediaRepo;

    //Logic handle delete Media of notes
    public void deleteMedia(UUID noteId, UUID mediaID){
        NoteMedia noteMedia = mediaRepo.findById(mediaID)
                .orElseThrow(() -> new IllegalArgumentException("File haven't uploaded yet! " + mediaID));

        if(!noteMedia.getNotes().getId().equals(noteId)){
            throw new IllegalArgumentException("Can not delete file !");
        }
        mediaRepo.delete(noteMedia);
    }
}
