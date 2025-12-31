package com.example.notesWeb.exception.redis.noteRedis;

import com.example.notesWeb.dtos.NoteDto.NoteRequest;
import com.example.notesWeb.exception.RedisStreamConsume;
import com.example.notesWeb.model.takeNotes.Notes;
import com.example.notesWeb.service.takeNotes.CreateNoteService;
import lombok.extern.slf4j.Slf4j;
import com.google.common.util.concurrent.RateLimiter;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class NoteRedisConsumer extends RedisStreamConsume {
//    @Autowired
//    private RedisTemplate<String, Object> redisTemplate;

    private final CreateNoteService createNoteService;

//    @Autowired(required = false)
//    private SimpMessagingTemplate messagingTemplate;

    private static final String sTREAM_kEY= "notes:create:stream";
    private static final String gROUP = "notes-group";
//    private static final String cONSUMER_nAME = "consumer-2";

    //Thread pool processes requests in parallel
//    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    private final RateLimiter limitRequest = RateLimiter.create(50.0);
//    private final Semaphore rateLimit = new Semaphore(50);

    public NoteRedisConsumer(
            RedisTemplate<String, Object> redisTemplate,
            CreateNoteService createNoteService
    ) {
        super(redisTemplate, sTREAM_kEY, gROUP, 10);
        this.createNoteService = createNoteService;
    }

//    @PostConstruct
//    public void noteConsumer(String consumerNote){
//        try{
//            redisTemplate.opsForStream().createGroup(sTREAM_kEY, ReadOffset.latest(), gROUP);
//            log.info("Created Redis Stream Group: {}", gROUP);
//        }catch (Exception ignored){
//            log.info("Group already existed: {}", gROUP);
//        }
//
//        Executors.newSingleThreadExecutor().submit(() -> {
//            log.info("NoteRedisConsumer stared, listening for new message....", consumerNote);
//            while (true){
//                try{
//                    //Read message each sec
//                    List<MapRecord<String, Object, Object>> recordList = redisTemplate.opsForStream()
//                            .read(Consumer.from(gROUP, consumerNote),
//                                    StreamReadOptions.empty().count(100).block(Duration.ofMillis(500)),
//                                    StreamOffset.create(sTREAM_kEY, ReadOffset.lastConsumed()));
//
//                    if (recordList == null || recordList.isEmpty()) continue;
//
//                    log.info("Received {} messages from Redis", recordList.size());
//
//                    if(recordList != null && !recordList.isEmpty()){
//                        for(MapRecord<String, Object, Object> record : recordList){
//                            if(!limitRequest.tryAcquire(500, TimeUnit.MICROSECONDS)) {
//                                log.warn("Too many request! Delaying message: {}", record.getId());
//                                Thread.sleep(200);
//                                continue;
//                            }
//
//                            rateLimit.acquire();
//
//                            executorService.submit(() -> {
//                                try {
//                                    handleMessageNote(record);
//                                } catch (Exception e) {
//                                    log.error("Error processing message: {}", e.getMessage(), e);
//                                } finally {
//                                    rateLimit.release();
//                                }
//                            });                        }
//                    }
//                }catch (Exception e){
//                    log.error("Redis consumer loop error: {}", e.getMessage());
//
//                    if (e.getMessage() != null && e.getMessage().contains("destroyed")) {
//                        log.warn("Redis connection was destroyed. Attempting to reinitialize RedisTemplat....");
//                        try{
//                            Thread.sleep(3000);
//                        }catch (InterruptedException ignored){}
//                        try {
//                            LettuceConnectionFactory factory = (LettuceConnectionFactory) redisTemplate.getConnectionFactory();
//                            if (factory != null && !factory.isRunning()) {
//                                factory.resetConnection();
//                                factory.start();
//                                log.info("Redis connection reinitialized successfully");
//                            }
//                        }catch (Exception ex) {
//                            log.error("Failed to reinitialize Redis connection: {}", ex.getMessage());
//                        }
//                    }
//
//                    try{
//                        Thread.sleep(500);
//                    }catch (InterruptedException ignored){}
//                }
//            }
//        });
//    }

//    private void handleMessageNote(MapRecord<String, Object, Object> recordNote){
//        if (!rateLimit.tryAcquire()) {
//            log.warn("Rate limited note create {}", recordNote.getId());
//            return;
//        }
//        try{
//            String content = (String) recordNote.getValue().get("content");
//            String title = (String) recordNote.getValue().get("title");
//            String username = (String) recordNote.getValue().get("username");
//
//            NoteRequest noteRequest = new NoteRequest(content, title);
//            log.info("Handle create note for user: {} - {}" , username, title);
//
//            Notes newNotes = createNoteService.createNote(noteRequest, username);
//            log.info("Notes created success: {} - ID: {}", title, newNotes.getId());
//
//            redisTemplate.opsForStream().acknowledge(sTREAM_kEY, gROUP, recordNote.getId());
//        }catch (Exception e){
//            log.error("Error handle message {}: {}", recordNote.getId(), e.getMessage(), e);
//        }
//    }

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

        try {
            Notes notes = createNoteService.createNote(
                    new NoteRequest(content, title),
                    username
            );
            log.info("Note created {} - {}", notes.getId(), title);
        }catch (Exception e) {
            log.error("Error handle message {}: {}", recordNote.getId(), e.getMessage(), e);
        }
    }
}
