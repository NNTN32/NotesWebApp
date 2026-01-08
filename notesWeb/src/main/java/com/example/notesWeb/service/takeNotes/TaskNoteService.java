package com.example.notesWeb.service.takeNotes;

import com.example.notesWeb.dtos.NoteDto.NoteRequest;
import com.example.notesWeb.dtos.NoteDto.NoteResponse;
import com.example.notesWeb.model.User;
import com.example.notesWeb.model.takeNotes.NoteMedia;
import com.example.notesWeb.model.takeNotes.Notes;
import com.example.notesWeb.repository.noteRepo.MediaRepo;
import com.example.notesWeb.repository.noteRepo.NotesRepo;
import com.example.notesWeb.repository.UserRepo;
import com.example.notesWeb.service.FailedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskNoteService {
    private final UserRepo userRepo;
    private final NotesRepo notesRepo;
    private final MediaRepo mediaRepo;

    //Logic take lists Note
    public List<Notes> getAllListNote(UUID userID){
        User user = userRepo.findById(userID)
                .orElseThrow(() -> new FailedException("User not found with ID:" + userID));
        return notesRepo.findByUserId(user.getId());
    }

    //Logic get list NoteID
    public List<NoteResponse> getListNoteID(UUID noteID) {
        Notes note = notesRepo.findNoteId(noteID)
                .orElseThrow(() -> new FailedException("Note doesn't exist! " + noteID));

        List<NoteResponse> responseList = new ArrayList<>();

        // Case if Notes have uploaded Media
        if (note.getNoteMediaList() != null && !note.getNoteMediaList().isEmpty()) {
            for (NoteMedia media : note.getNoteMediaList()) {
                responseList.add(new NoteResponse(
                        note.getTitle(),
                        note.getContent(),
                        media.getUrl()
                ));
            }
        } else {
            // Case if Notes have not uploaded Media yet
            responseList.add(new NoteResponse(
                    note.getTitle(),
                    note.getContent(),
                    null
            ));
        }

        return responseList;
    }

    //Logic delete Post
    public void deleteNote(UUID noteID, UUID userID){
        Notes note = notesRepo.findNoteId(noteID)
                .orElseThrow(() -> new IllegalArgumentException("Note doesn't exist! " + noteID));

        if (!note.getUser().getId().equals(userID)) {
            throw new AccessDeniedException("You are not authorized to delete this note!");
        }

        notesRepo.delete(note);
    }

    //Logic handle update Note
    public Notes updateNote(NoteRequest noteRequest, UUID noteID, UUID userID){
        User user = userRepo.findById(userID)
                .orElseThrow(() -> new FailedException("User not found with ID:" + userID));

        Notes notes = notesRepo.findNote(noteID)
                .orElseThrow(() -> new FailedException("Note doesn't exist! " + noteID));

        //Checking authority of owner Notes
        if(!notes.getUser().getId().equals(user.getId())){
            throw new AccessDeniedException("You're not authorized to update this Notes!");
        }

        //Main logic update what field user input
        if(noteRequest.getTitle() != null && !noteRequest.getTitle().isBlank()){
            notes.setTitle(noteRequest.getTitle());
        }
        if(noteRequest.getContent() != null && !noteRequest.getContent().isBlank()){
            notes.setContent(noteRequest.getContent());
        }

        notes.setUpdatedAt(LocalDateTime.now());
        return notesRepo.save(notes);
    }
}
