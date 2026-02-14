package com.example.notesWeb.exception.redis.mediaNoteRedis;

import com.example.notesWeb.dtos.NoteDto.MediaNoteRequest;
import com.example.notesWeb.exception.RedisStreamConsume;
import com.example.notesWeb.service.FailedException;
import com.example.notesWeb.service.SystemException;
import com.example.notesWeb.service.takeNotes.MediaNoteService;
import lombok.extern.slf4j.Slf4j;
import com.google.common.util.concurrent.RateLimiter;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class MediaRedisConsumer extends RedisStreamConsume {
    private final MediaNoteService mediaNoteService;
    private final RateLimiter limitRequestMedia = RateLimiter.create(50.0);
    private static final String key_STREAM = "media:create:stream";
    private static final String media_GROUP = "media-group";

    public MediaRedisConsumer(
            RedisTemplate<String, Object> redisTemplate,
            MediaNoteService mediaNoteService
    ) {
        super(redisTemplate, key_STREAM, media_GROUP, 8);
        this.mediaNoteService = mediaNoteService;
    }

    @Override
    protected void handleMessage(MapRecord<String, Object, Object> recordMedia){
        if (!limitRequestMedia.tryAcquire()) {
            log.warn("Media rate limited {}", recordMedia.getId());
            redisTemplate.opsForStream()
                    .acknowledge(key_STREAM, media_GROUP, recordMedia.getId());
            return;
        }

        String recordId = recordMedia.getId().getValue();
        try{
            Map<Object, Object> map = recordMedia.getValue();
            UUID postID = UUID.fromString(map.get("postID").toString());
            String tempUrl = map.get("tempUrl").toString();
            String fileName = map.get("fileName").toString();
            String contentType = map.get("contentType").toString();
            String requestId = map.get("requestId").toString();

            String idempotentKey = "media:req:" + requestId;
            Boolean locked = redisTemplate.opsForValue()
                    .setIfAbsent(idempotentKey, "PROCESSING", Duration.ofHours(1));


            //Hidden bug -> race condition between 2 consumer upload same time
            if (Boolean.FALSE.equals(locked)) {
                log.warn("Duplicate media request {}", requestId);
                redisTemplate.opsForStream()
                        .acknowledge(key_STREAM, media_GROUP, recordMedia.getId());
                return;
            }

            try{
                log.info("Processing media message {} for post {}", recordId, postID);

                //Reload file from Cloudinary temp URL
                try (InputStream inputStream = openCloudinaryStream(tempUrl)){
                    MockMultipartFile multipartFile = new MockMultipartFile(
                            "file", fileName, contentType, inputStream
                    );

                    mediaNoteService.uploadMedia(new MediaNoteRequest(multipartFile), postID);

                    redisTemplate.opsForValue().set(idempotentKey, "DONE", Duration.ofHours(1));

                    redisTemplate.opsForStream().acknowledge(key_STREAM, media_GROUP, recordMedia.getId());

                    log.info("Media uploaded for post {}", postID);

                }catch (FailedException e) {
                    //business logic fail -> ack (delete msg caching)
                    redisTemplate.opsForStream().acknowledge(key_STREAM, media_GROUP, recordMedia.getId());

                }catch (SystemException e) {
                    //system fail (db down, cloudinary timeout) -> retry -> release lock
                    redisTemplate.delete(idempotentKey);
                    throw e;
                }
            } catch (Exception e) {
                //poison message -> ack -> release lock
                redisTemplate.delete(idempotentKey);
                redisTemplate.opsForStream().acknowledge(key_STREAM, media_GROUP, recordMedia.getId());
            }

        }catch (FailedException e){
            //No retry
            log.warn("Media failed for record {}: {}", recordId, e.getMessage());
            redisTemplate.opsForStream()
                    .acknowledge(key_STREAM, media_GROUP, recordMedia.getId());
        } catch (SystemException e) {
            //Can retry
            log.error("Media system error for record {}", recordId, e);
        }
        //debug for swallow exception, no ack, re-deliver -> duplication upload file
        catch (Exception e) {
            log.error("Failed to process record {}: {}", recordId, e);

            //ack for prevent poison message
            redisTemplate.opsForStream()
                    .acknowledge(key_STREAM, media_GROUP, recordMedia.getId());
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
