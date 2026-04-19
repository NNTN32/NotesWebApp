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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    public void consume(NoteUpdateEvent noteUpdateEvent, Acknowledgment acknowledgment) {
        log.info("Consuming update for note {}", noteUpdateEvent.getNoteID());

        try {
//            if (noteUpdateEvent.getNoteRequest().getTitle() != null && noteUpdateEvent.getNoteRequest().getTitle().contains("FAIL_TEST")) {
//                throw new RuntimeException("Cố tình gây lỗi để test DLT nè Nhân ơi!");
//            }
            //Service return back to class DTO already formated for Cache
            Notes updateNote = taskNoteService.updateNote(noteUpdateEvent);

            if (updateNote == null) {
                log.warn("Note is being processed via Fallback. Consumer is now finished!");
                acknowledgment.acknowledge();
                return;
            }

            //Side effect: Cache & Notify
            handleSideEffects(updateNote, noteUpdateEvent.getUsername());

            //Prevent kafka retry, confirm update success
            acknowledgment.acknowledge();
            log.info("Note update completed: {}", updateNote.getId());

        } catch (Exception e) {
            log.error("Conflict detected for note {}. Someone else updated it!", noteUpdateEvent.getNoteID());
            taskNoteService.handleFailure(noteUpdateEvent, e);
            acknowledgment.acknowledge();
        }
    }

    private void handleSideEffects (Notes updateNote, String username) {
        try{
            NoteCache cacheDTO = NoteCache.fromEntity(updateNote);
            String noteJson = objectMapper.writeValueAsString(cacheDTO);
            String redisKey = "note:" + updateNote.getId();

            redisTemplate.opsForValue().set(redisKey, noteJson);

            //Send realtime through STOMP after updated notes
            //Using username from event no need to Query DB
            messagingTemplate.convertAndSendToUser(username, "/queue/note-updates", cacheDTO);

            log.info("Note updated & cached successfully: {}",updateNote.getId());
        } catch (Exception e) {
            log.warn("STOMP/Redis notify failed, but DB was updated: {}", updateNote.getId(), e.getMessage());
        }
    }


    @PreDestroy
    public void shutdown() {
        log.info("Kafka Note Consumer shutting down...");
    }
}
