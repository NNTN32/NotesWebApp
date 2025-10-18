package com.example.notesWeb.exception.redis.mediaNoteRedis;

import com.example.notesWeb.dtos.NoteDto.MediaNoteRequest;
import com.example.notesWeb.service.takeNotes.MediaNoteService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.google.common.util.concurrent.RateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaRedisConsumer {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private MediaNoteService mediaNoteService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(8);

    private final RateLimiter limitRequestMedia = RateLimiter.create(50.0);
    private final Semaphore rateLimitMedia = new Semaphore(50);

    private static final String key_STREAM = "media:create:stream";
    private static final String media_GROUP = "media-group";
//    private static final String media_CONSUMER = "consumer-3";

//    @PostConstruct
    public void initGroup(){
        try{
            redisTemplate.opsForStream().createGroup(key_STREAM, ReadOffset.from("0-0"), media_GROUP);
        } catch (Exception e) {
            log.info("Group may already exist: {}", e.getMessage());
        }
    }

    //@Scheduled function has no parameters
    @Scheduled(fixedDelay = 500)
    public void scheduledMediaConsumer() {
        mediaConsumer("consumer-3");
    }

    public void mediaConsumer(String consumerMedia) {
        List<MapRecord<String, Object, Object>> mediaRecords = redisTemplate.opsForStream().read(
                Consumer.from(media_GROUP, consumerMedia),
                StreamReadOptions.empty().count(30).block(Duration.ofSeconds(1)),
                StreamOffset.create(key_STREAM, ReadOffset.lastConsumed())
        );

        if (mediaRecords == null || mediaRecords.isEmpty()) return;

        for (MapRecord<String, Object, Object> record : mediaRecords){
            try{
                if (!limitRequestMedia.tryAcquire(500, TimeUnit.MILLISECONDS)){
                    log.warn("Too many request! Delyaing message: {}", record.getId());
                    Thread.sleep(200);
                    continue;
                }
                rateLimitMedia.acquire();

                executorService.submit(() -> {
                    try {
                        handleMessageMedia(record);
                    } catch (Exception e) {
                        log.error("Error processing message: {}", e.getMessage(), e);
                    }finally {
                        rateLimitMedia.release();
                    }
                });
            }catch (Exception e){
                log.error("Redis consumer loop error: {}", e.getMessage());
                try{
                    Thread.sleep(500);
                }catch (InterruptedException ignored){}
            }
        }
    }

    private void handleMessageMedia(MapRecord<String, Object, Object> recordMedia){
        String recordId = recordMedia.getId().getValue();
        try{
            Map<Object, Object> map = recordMedia.getValue();
            UUID postID = UUID.fromString(map.get("postID").toString());
            String tempUrl = map.get("tempUrl").toString();
            String fileName = map.get("fileName").toString();
            String contentType = map.get("contentType").toString();

            log.info("Processing media message {} for post {}", recordId, postID);

            //Reload file from Cloudinary temp URL
            try (InputStream inputStream = new URL(tempUrl).openStream()){
                MockMultipartFile multipartFile = new MockMultipartFile(
                        "file",
                        fileName,
                        contentType,
                        inputStream
                );

                mediaNoteService.uploadMedia(new MediaNoteRequest(multipartFile), postID);

                redisTemplate.opsForStream().acknowledge(key_STREAM, media_GROUP, recordMedia.getId());
                log.info("Processed and saved media for postId={} from {}", postID, tempUrl);
            }
        } catch (Exception e) {
            log.error("Failed to process record {}: {}", recordId, e.getMessage());
        }
    }

    private InputStream openCloudinaryStream(String tempUrl) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(tempUrl).openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        return conn.getInputStream();
    }
}
