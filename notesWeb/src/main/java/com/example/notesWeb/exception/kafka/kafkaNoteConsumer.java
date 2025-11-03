package com.example.notesWeb.exception.kafka;


import com.example.notesWeb.dtos.NoteDto.NoteCache;
import com.example.notesWeb.dtos.NoteDto.NoteRequest;
import com.example.notesWeb.dtos.NoteDto.NoteUpdateEvent;
import com.example.notesWeb.model.User;
import com.example.notesWeb.model.takeNotes.Notes;
import com.example.notesWeb.repository.UserRepo;
import com.example.notesWeb.repository.noteRepo.NotesRepo;
import com.example.notesWeb.service.takeNotes.TaskNoteService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class kafkaNoteConsumer {
    private final TaskNoteService taskNoteService;
    private final NotesRepo notesRepo;
    private final UserRepo userRepo;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @KafkaListener(topics = "note-updates", groupId = "note-update-group")
    public void consume(NoteUpdateEvent noteUpdateEvent) {
        try{
            Notes updateNote = taskNoteService.updateNote(
                    noteUpdateEvent.getNoteRequest(),
                    noteUpdateEvent.getNoteID(),
                    noteUpdateEvent.getUserID()
            );

            NoteCache cacheDTO = NoteCache.fromEntity(updateNote);
            try {
                String noteJson = objectMapper.writeValueAsString(cacheDTO);
                String redisKey = "note:" + updateNote.getId();

                redisTemplate.opsForValue().set(redisKey, noteJson);
                log.info("Note updated & cached successfully: {}",updateNote.getId());

                //Send realtime through STOMP after updated notes
                User user = userRepo.findById(noteUpdateEvent.getUserID())
                        .orElseThrow(() -> new RuntimeException("User not found!"));
                messagingTemplate.convertAndSendToUser(user.getUsername(), "/queue/note-updates", cacheDTO);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize note {} for Redis: {}", updateNote.getId(), e.getMessage());
            } catch (DataAccessException redisEx) {
                log.warn("Redis unavailable, skipping cache update for note {}. Error: {}", updateNote.getId(), redisEx.getMessage());
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
