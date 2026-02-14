package com.example.notesWeb.exception.redis.noteRedis;

import com.example.notesWeb.dtos.NoteDto.NoteRequest;
import com.example.notesWeb.exception.RedisStreamConsume;
import com.example.notesWeb.model.takeNotes.Notes;
import com.example.notesWeb.service.FailedException;
import com.example.notesWeb.service.takeNotes.CreateNoteService;
import lombok.extern.slf4j.Slf4j;
import com.google.common.util.concurrent.RateLimiter;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class NoteRedisConsumer extends RedisStreamConsume {
    private final CreateNoteService createNoteService;
    private static final String sTREAM_kEY= "notes:create:stream";
    private static final String gROUP = "notes-group";

    private final RateLimiter limitRequest = RateLimiter.create(50.0);

    public NoteRedisConsumer(
            RedisTemplate<String, Object> redisTemplate,
            CreateNoteService createNoteService
    ) {
        super(redisTemplate, sTREAM_kEY, gROUP, 10);
        this.createNoteService = createNoteService;
    }

    @Override
    protected void handleMessage(MapRecord<String, Object, Object> recordNote) {
        if (!limitRequest.tryAcquire()) {
            log.warn("Note create rate limited {}", recordNote.getId());
            return;
        }

        Map<Object, Object> n = recordNote.getValue();
        String content = (String) n.get("content");
        String title = (String) n.get("title");
        String username = (String) n.get("username");

        String requestNote = "note:req:" + recordNote.getId().getValue();

        //Idempotent
        if (Boolean.TRUE.equals(redisTemplate.hasKey(requestNote))) {
            log.info("Duplicate note create reuqest {}", recordNote.getId());
            return;
        }
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(requestNote, "PROCESSING", Duration.ofMinutes(5));
        if (Boolean.FALSE.equals(locked)) {
            return;
        }

        try {
            Notes notes = createNoteService.createNote(
                    new NoteRequest(content, title),
                    username
            );
            redisTemplate.opsForValue()
                            .set(requestNote, "DONE", Duration.ofMinutes(10));
            log.info("Note created {} - {}", notes.getId(), title);
        }
        catch (FailedException e) {
            redisTemplate.opsForValue()
                            .set(requestNote, "FAILED", Duration.ofMinutes(2));
            log.warn("Create note failed for user {} - log: {}", username, e.getMessage());
        }
        //Bug no retry, replay
        catch (Exception e) {
            //system fail -> retry
            redisTemplate.delete(requestNote);
            log.error("Error handle message {}: {}", recordNote.getId(), e.getMessage(), e);
        }
    }
}
