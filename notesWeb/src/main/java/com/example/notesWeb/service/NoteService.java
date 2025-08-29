package com.example.notesWeb.service;

import com.example.notesWeb.dtos.NoteDto.NoteRequest;
import com.example.notesWeb.model.User;
import com.example.notesWeb.model.takeNotes.Notes;
import com.example.notesWeb.repository.NotesRepo;
import com.example.notesWeb.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NoteService {
    private final UserRepo userRepo;
    private final NotesRepo notesRepo;

    //Logic create notes for user
    public Notes createNote(NoteRequest noteRequest, String username){
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Notes notes = new Notes();
        notes.setContent(noteRequest.getContent());
        notes.setCreatedAt(noteRequest.getCreatedAt());

        return notesRepo.save(notes);
    }
}
