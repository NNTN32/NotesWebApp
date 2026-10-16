package com.example.notesWeb.service.takeNotes;

import com.example.notesWeb.dtos.NoteDto.NoteRequest;
import com.example.notesWeb.model.User;
import com.example.notesWeb.model.takeNotes.Notes;
import com.example.notesWeb.repository.IdGenerateRepo;
import com.example.notesWeb.repository.noteRepo.NotesRepo;
import com.example.notesWeb.repository.UserRepo;
import com.example.notesWeb.service.FailedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CreateNoteService {
    private final com.example.notesWeb.messaging.Inbox inbox;
    private final UserRepo userRepo;
    private final NotesRepo notesRepo;
    private final IdGenerateRepo idGenerateRepo;

    @org.springframework.transaction.annotation.Transactional
    public void createOnce(String eventKey, NoteRequest request, String username) {
        if (inbox.claim(eventKey)) createNote(request, username);
    }

    //Logic create notes for user
    public Notes createNote(NoteRequest noteRequest, String username){
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new FailedException("User not found"));

        Notes notes = new Notes();
        notes.setId(idGenerateRepo.nextId());
        notes.setTitle(noteRequest.getTitle());
        notes.setContent(noteRequest.getContent());
        notes.setUser(user);

        return notesRepo.save(notes);
    }
}
