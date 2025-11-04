package com.example.notesWeb.exception.redis.noteRedis;

import com.example.notesWeb.dtos.NoteDto.NoteRequest;
import com.example.notesWeb.model.takeNotes.Notes;
import com.example.notesWeb.service.takeNotes.CreateNoteService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.google.common.util.concurrent.RateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

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
//    private static final String cONSUMER_nAME = "consumer-2";

    //Thread pool processes requests in parallel
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    private final RateLimiter limitRequest = RateLimiter.create(50.0);
    private final Semaphore rateLimit = new Semaphore(50);

//    @PostConstruct
    public void noteConsumer(String consumerNote){
        try{
            redisTemplate.opsForStream().createGroup(sTREAM_kEY, ReadOffset.latest(), gROUP);
            log.info("Created Redis Stream Group: {}", gROUP);
        }catch (Exception ignored){
            log.info("Group already existed: {}", gROUP);
        }

        Executors.newSingleThreadExecutor().submit(() -> {
            log.info("NoteRedisConsumer stared, listening for new message....", consumerNote);
            while (true){
                try{
                    //Read message each sec
                    List<MapRecord<String, Object, Object>> recordList = redisTemplate.opsForStream()
                            .read(Consumer.from(gROUP, consumerNote),
                                    StreamReadOptions.empty().count(100).block(Duration.ofMillis(500)),
                                    StreamOffset.create(sTREAM_kEY, ReadOffset.lastConsumed()));

                    if (recordList == null || recordList.isEmpty()) continue;

                    log.info("Received {} messages from Redis", recordList.size());

                    if(recordList != null && !recordList.isEmpty()){
                        for(MapRecord<String, Object, Object> record : recordList){
                            if(!limitRequest.tryAcquire(500, TimeUnit.MICROSECONDS)) {
                                log.warn("Too many request! Delaying message: {}", record.getId());
                                Thread.sleep(200);
                                continue;
                            }

                            rateLimit.acquire();

                            executorService.submit(() -> {
                                try {
                                    handleMessageNote(record);
                                } catch (Exception e) {
                                    log.error("Error processing message: {}", e.getMessage(), e);
                                } finally {
                                    rateLimit.release();
                                }
                            });                        }
                    }
                }catch (Exception e){
                    log.error("Redis consumer loop error: {}", e.getMessage());

                    if (e.getMessage() != null && e.getMessage().contains("destroyed")) {
                        log.warn("Redis connection was destroyed. Attempting to reinitialize RedisTemplat....");
                        try{
                            Thread.sleep(3000);
                        }catch (InterruptedException ignored){}
                        try {
                            LettuceConnectionFactory factory = (LettuceConnectionFactory) redisTemplate.getConnectionFactory();
                            if (factory != null && !factory.isRunning()) {
                                factory.resetConnection();
                                factory.start();
                                log.info("Redis connection reinitialized successfully");
                            }
                        }catch (Exception ex) {
                            log.error("Failed to reinitialize Redis connection: {}", ex.getMessage());
                        }
                    }

                    try{
                        Thread.sleep(500);
                    }catch (InterruptedException ignored){}
                }
            }
        });
    }

    private void handleMessageNote(MapRecord<String, Object, Object> recordNote){
        if (!rateLimit.tryAcquire()) {
            log.warn("Rate limited note create {}", recordNote.getId());
            return;
        }
        try{
            String content = (String) recordNote.getValue().get("content");
            String title = (String) recordNote.getValue().get("title");
            String username = (String) recordNote.getValue().get("username");

            NoteRequest noteRequest = new NoteRequest(content, title);
            log.info("Handle create note for user: {} - {}" , username, title);

            Notes newNotes = createNoteService.createNote(noteRequest, username);
            log.info("Notes created success: {} - ID: {}", title, newNotes.getId());

            redisTemplate.opsForStream().acknowledge(sTREAM_kEY, gROUP, recordNote.getId());
        }catch (Exception e){
            log.error("Error handle message {}: {}", recordNote.getId(), e.getMessage(), e);
        }
    }
}
