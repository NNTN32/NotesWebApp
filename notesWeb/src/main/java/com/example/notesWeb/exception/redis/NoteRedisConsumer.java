package com.example.notesWeb.exception.redis;

import com.example.notesWeb.dtos.NoteDto.NoteRequest;
import com.example.notesWeb.dtos.NoteDto.NoteResponse;
import com.example.notesWeb.model.takeNotes.Notes;
import com.example.notesWeb.service.takeNotes.CreateNoteService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class NoteRedisConsumer {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private CreateNoteService createNoteService;

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    private static final String sTREAM_kEY= "notes:create:stream";
    private static final String gROUP = "notes-group";
    private static final String cONSUMER_nAME = "consumer-2";

    @PostConstruct
    public void noteConsumer(){
        try{
            redisTemplate.opsForStream().createGroup(sTREAM_kEY, ReadOffset.latest(), gROUP);
            log.info("Created Redis Stream Group: {}", gROUP);
        }catch (Exception ignored){
            log.info("Group already existed: {}", gROUP);
        }

        Executors.newSingleThreadExecutor().submit(() -> {
            while (true){
                try{
                    //Read message each sec
                    List<MapRecord<String, Object, Object>> recordList = redisTemplate.opsForStream()
                            .read(Consumer.from(gROUP, cONSUMER_nAME),
                                    StreamReadOptions.empty().count(10).block(Duration.ofSeconds(1)),
                                    StreamOffset.create(sTREAM_kEY, ReadOffset.lastConsumed()));

                    if(recordList != null){
                        for (MapRecord<String, Object, Object> record : recordList) {
                            try{
                                String content = (String) record.getValue().get("content");
                                String title = (String) record.getValue().get("title");
                                String username = (String) record.getValue().get("username");

                                log.info("Received create notes request from user: {}", username);

                                NoteRequest noteRequest = new NoteRequest(content, title);
                                Notes savedNotes = createNoteService.createNote(noteRequest, username);

                                NoteResponse response = new NoteResponse(
                                        savedNotes.getContent(),
                                        savedNotes.getTitle(),
                                        "/notes/" + savedNotes.getId()
                                );

                                if(messagingTemplate != null){
                                    messagingTemplate.convertAndSendToUser(username, "/queue/note-result", response);

                                }

                                //Save into Redis Stream above 1 min
                                String keyResult = "note:result" + savedNotes.getId();
                                Map<String , String> resultMap = new HashMap<>();
                                resultMap.put("content", savedNotes.getContent());
                                resultMap.put("title", savedNotes.getTitle());
                                resultMap.put("url", "/notes/" + savedNotes.getId());

                                redisTemplate.opsForHash().putAll(keyResult, resultMap);
                                redisTemplate.expire(keyResult, Duration.ofMinutes(1));

                                //Confirm handle message queue
                                redisTemplate.opsForStream().acknowledge(sTREAM_kEY, gROUP, record.getId());
                                log.info("Notes created successfully for user: {}", username);
                            }catch (Exception e){
                                log.error("Failed to process create note message: {}", e.getMessage());
                            }
                        }
                    }
                }catch (Exception e){
                    log.error("Redis consumer loop error: {}", e.getMessage());
                }
            }
        });
    }
}
