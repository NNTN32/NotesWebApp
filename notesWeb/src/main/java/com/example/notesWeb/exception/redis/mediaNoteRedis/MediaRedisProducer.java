package com.example.notesWeb.exception.redis.mediaNoteRedis;

import com.example.notesWeb.service.SystemException;
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
    private final com.example.notesWeb.repository.noteRepo.NotesRepo notesRepo;
    private static final String key_STREAM = "media:create:stream";

    public void sendMediaRequest(String username, UUID userId, UUID postID, MultipartFile file){
        if (!notesRepo.existsByIdAndUserId(postID, userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Note access denied");
        }
        try{
            Map<String, String> uploadResult = securityUploadService.uploadCloudinary(file);

            String finalUrl = uploadResult.get("secure_url");
            String resourceType = uploadResult.get("resource_type");

            Map<String, String> mediaFields = new HashMap<>();
            mediaFields.put("requestId", UUID.randomUUID().toString());
            mediaFields.put("username", username);
            mediaFields.put("postID", postID.toString());
            mediaFields.put("finalUrl", finalUrl);
            mediaFields.put("resourceType", resourceType);

            System.out.println("Sending request to Redis Stream: " + mediaFields);
            redisTemplate.opsForStream()
                    .add(StreamRecords.newRecord()
                            .in(key_STREAM)
                            .ofMap(mediaFields));
            log.info("Enqueued media metadata for post {}: URL {}", postID, finalUrl);

        }catch (Exception e){
            log.error("Failed to enqueue upload: {}", e.getMessage());
            throw new SystemException("Failed to enqueue upload task: ", e);
        }
    }
}
