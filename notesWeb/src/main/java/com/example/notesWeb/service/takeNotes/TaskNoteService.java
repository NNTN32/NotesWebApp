package com.example.notesWeb.service.takeNotes;

import com.example.notesWeb.dtos.NoteDto.NoteRequest;
import com.example.notesWeb.dtos.NoteDto.NoteResponse;
import com.example.notesWeb.dtos.NoteDto.NoteUpdateEvent;
import com.example.notesWeb.model.User;
import com.example.notesWeb.model.takeNotes.Notes;
import com.example.notesWeb.repository.noteRepo.NotesRepo;
import com.example.notesWeb.repository.UserRepo;
import com.example.notesWeb.service.FailedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskNoteService {
    private final com.example.notesWeb.messaging.Inbox inbox;
    private final com.example.notesWeb.messaging.Outbox outbox;
    private final UserRepo userRepo;
    private final NotesRepo notesRepo;
    private final ObjectMapper objectMapper;

    //Logic take lists Note
    public List<Notes> getAllListNote(UUID userID){
        User user = userRepo.findById(userID)
                .orElseThrow(() -> new FailedException("User not found with ID:" + userID));
        return notesRepo.findByUserId(user.getId());
    }

    //Logic get list NoteID
    public NoteResponse getListNoteID(UUID noteID, UUID currentUserID) {
        Notes note = notesRepo.findNoteId(noteID, currentUserID)
                .orElseThrow(() -> new FailedException("Note doesn't exist! " + noteID));

        List<String> mediaUrls = (note.getNoteMediaList() == null) ? List.of() :
                //Map through to class DTO by Java Stream
                note.getNoteMediaList().stream()
                        .map(media -> media.getUrl())
                        .collect(Collectors.toList());
        return NoteResponse.builder()
                .title(note.getTitle())
                .content(note.getContent())
                .url(mediaUrls)
                .build();
    }

    //Logic delete Post
    public void deleteNote(UUID noteID, UUID userID){
        Notes note = notesRepo.findNoteId(noteID, userID)
                .orElseThrow(() -> new IllegalArgumentException("Note doesn't exist! " + noteID));

        if (!note.getUser().getId().equals(userID)) {
            throw new AccessDeniedException("You are not authorized to delete this note!");
        }

        notesRepo.delete(note);
    }

    //Logic handle update Note
    @Transactional
    public Notes updateNote(NoteUpdateEvent updateEvent){
        UUID userID = updateEvent.getUserID();
        UUID noteID = updateEvent.getNoteID();
        NoteRequest noteRequest = updateEvent.getNoteRequest();

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


    @Transactional
    public void updateOnce(String key, NoteUpdateEvent event) {
        if (!inbox.claim(key)) return;
        Notes updated = updateNote(event);
        try {
            String payload = objectMapper.writeValueAsString(com.example.notesWeb.dtos.NoteDto.NoteCache.fromEntity(updated));
            java.util.UUID id = java.util.UUID.nameUUIDFromBytes(("notify:"+key).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            outbox.append(id, "STOMP", event.getUsername(), "/queue/note-updates", payload, java.time.Instant.now());
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalArgumentException("Cannot serialize note notification");
        }
    }
}
