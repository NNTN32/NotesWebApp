package com.example.notesWeb.exception.redis.mediaNoteRedis;

import com.example.notesWeb.dtos.NoteDto.MediaNoteRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaRedisProducer {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    private final SecurityUploadService securityUploadService;
    private static final String key_STREAM = "media:create:stream";

    public void sendMediaRequest(MultipartFile file, UUID postID, String username){
        try{
            String tempUrl = securityUploadService.uploadTemporary(file);
            Map<String, String> mediaFields = new HashMap<>();
            mediaFields.put("username", username);
            mediaFields.put("postID", postID.toString());
            mediaFields.put("tempUrl", tempUrl);
            mediaFields.put("fileName", file.getOriginalFilename());
            mediaFields.put("contentType", file.getContentType());

            System.out.println("Sending request to Redis Stream: " + mediaFields);
            redisTemplate.opsForStream()
                    .add(StreamRecords.newRecord()
                            .in("media:create:stream")
                            .ofMap(mediaFields));

        }catch (Exception e){
            log.error("Failed to enqueue upload: {}", e.getMessage());
            throw new RuntimeException("Failed to enqueue upload task: " + e.getMessage());
        }
    }
}
