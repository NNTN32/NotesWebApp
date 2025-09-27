package com.example.notesWeb.service.takeNotes;

import com.example.notesWeb.dtos.NoteDto.NoteRequest;
import com.example.notesWeb.dtos.NoteDto.NoteResponse;
import com.example.notesWeb.model.User;
import com.example.notesWeb.model.takeNotes.Notes;
import com.example.notesWeb.repository.MediaRepo;
import com.example.notesWeb.repository.NotesRepo;
import com.example.notesWeb.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskNoteService {
    private final UserRepo userRepo;
    private final NotesRepo notesRepo;
    private final MediaRepo mediaRepo;

    //Logic take lists Note
    public List<Notes> getAllListNote(Long userID){
        User user = userRepo.findById(userID)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with ID:" + userID));
        try{
            return notesRepo.findByUserId(user.getId());
        }catch (Exception e){
            throw new RuntimeException("Failed to get list Notes! " + e.getMessage());
        }
    }


}
