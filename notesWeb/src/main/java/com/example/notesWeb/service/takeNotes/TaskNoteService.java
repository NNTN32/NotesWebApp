package com.example.notesWeb.service.takeNotes;

import com.example.notesWeb.dtos.NoteDto.NoteRequest;
import com.example.notesWeb.dtos.NoteDto.NoteResponse;
import com.example.notesWeb.dtos.NoteDto.NoteUpdateEvent;
import com.example.notesWeb.dtos.NoteDto.stagingDTO.ErrorResponse;
import com.example.notesWeb.model.User;
import com.example.notesWeb.model.takeNotes.NoteMedia;
import com.example.notesWeb.model.takeNotes.Notes;
import com.example.notesWeb.model.takeNotes.StagedUpdate;
import com.example.notesWeb.repository.noteRepo.MediaRepo;
import com.example.notesWeb.repository.noteRepo.NotesRepo;
import com.example.notesWeb.repository.UserRepo;
import com.example.notesWeb.repository.noteRepo.StagingRepo;
import com.example.notesWeb.service.FailedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskNoteService {
    private final UserRepo userRepo;
    private final NotesRepo notesRepo;
    private final MediaRepo mediaRepo;
    private final ObjectMapper objectMapper;
    private final StagingRepo stagingRepo;
    private final SimpMessagingTemplate messagingTemplate;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

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
                        .map(NoteMedia::getUrl)
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
    @CircuitBreaker(name = "noteUpdate", fallbackMethod = "handleFailure")
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

    public Notes handleFailure (NoteUpdateEvent updateEvent, Throwable t) {
        log.error(">>> [CIRCUIT-BREAKER] Short circuit or major error for Note: {}. Reason: {}", updateEvent.getNoteID(), t.getMessage());
        try {
            ErrorResponse errorNotify = new ErrorResponse("Saving updates!");
            messagingTemplate.convertAndSendToUser(
                    updateEvent.getUsername(),
                    "/queue/note-updates",
                    errorNotify
            );
        } catch (Exception e) {
            log.warn("STOMP notify failed: {}", e.getMessage());
        }
        try {
            StagedUpdate failedEvent = new StagedUpdate();
            failedEvent.setNoteID(updateEvent.getNoteID());
            failedEvent.setUserID(updateEvent.getUserID());
            failedEvent.setRawPayload(objectMapper.writeValueAsString(updateEvent));
            failedEvent.setErrorReason(t.getMessage());
            failedEvent.setRetryCount(0);
            failedEvent.setCreatedAt(LocalDateTime.now());

            stagingRepo.save(failedEvent);
            log.info(">>> [STAGING] Error message saved to database for later processing!");

        } catch (Exception e) {
            log.error(">>> [CRITICAL] Unable to save the entire error table! Check the system immediately!", e.getMessage());
        }
        return null;
    }
}
