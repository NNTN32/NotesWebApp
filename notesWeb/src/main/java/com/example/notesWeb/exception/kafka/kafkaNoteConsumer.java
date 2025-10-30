package com.example.notesWeb.exception.kafka;


import com.example.notesWeb.dtos.NoteDto.NoteCache;
import com.example.notesWeb.dtos.NoteDto.NoteRequest;
import com.example.notesWeb.dtos.NoteDto.NoteUpdateEvent;
import com.example.notesWeb.model.User;
import com.example.notesWeb.model.takeNotes.Notes;
import com.example.notesWeb.repository.UserRepo;
import com.example.notesWeb.repository.noteRepo.NotesRepo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class kafkaNoteConsumer {
    private final NotesRepo notesRepo;
    private final UserRepo userRepo;
    private final ObjectMapper objectMapper;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @KafkaListener(topics = "note-updates", groupId = "note-update-group")
    public void consume(NoteUpdateEvent noteUpdateEvent) {
        try{
            UUID noteID = noteUpdateEvent.getNoteID();
            UUID userID = noteUpdateEvent.getUserID();
            NoteRequest noteRequest = noteUpdateEvent.getNoteRequest();

            User user = userRepo.findById(userID)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found!"));

            Notes notes = notesRepo.findNote(noteID)
                    .orElseThrow(() -> new IllegalArgumentException("Note not found!"));

            if (!notes.getUser().getId().equals(user.getId())) {
                throw new AccessDeniedException("Unauthorized to update note!");
            }

            if (noteRequest.getTitle() != null && !noteRequest.getTitle().isBlank()) {
                notes.setTitle(noteRequest.getTitle());
            }

            if (noteRequest.getContent() != null && !noteRequest.getContent().isBlank()) {
                notes.setContent(noteRequest.getContent());
            }

            notes.setUpdatedAt(LocalDateTime.now());
            notesRepo.save(notes);

            //Updated cache right after saved notes
//            String redisKey = "note:" + noteID;
//            redisTemplate.opsForValue().set(redisKey, notes);
//
//            log.info("Note updated & cached sucessfully: {}", noteID);

            NoteCache cacheDTO = NoteCache.fromEntity(notes);
            try {
                String noteJson = objectMapper.writeValueAsString(cacheDTO);
                String redisKey = "note:" + noteID;

                redisTemplate.opsForValue().set(redisKey, noteJson);
                log.info("Note updated & cached successfully: {}", noteID);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize note {} for Redis: {}", noteID, e.getMessage());
            } catch (DataAccessException redisEx) {
                log.warn("Redis unavailable, skipping cache update for note {}. Error: {}", noteID, redisEx.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to process note update: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Kafka Note Consumer shutting down...");
    }
}
