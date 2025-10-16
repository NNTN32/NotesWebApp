package com.example.notesWeb.exception.redis.mediaNoteRedis;

import com.example.notesWeb.dtos.NoteDto.MediaNoteRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaRedisProducer {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String key_STREAM = "media:create:stream";

    public void sendMediaRequest(MediaNoteRequest mediaNoteRequest, UUID postID){
        Map<String, Object> mediaFields = new HashMap<>();
        mediaFields.put("file", mediaNoteRequest.getFile());
        mediaFields.put("postID", postID);

        System.out.println("Sending request to Redis Stream: " + mediaFields);
        redisTemplate.opsForStream()
                .add(StreamRecords.newRecord()
                        .in("media:create:stream")
                        .ofMap(mediaFields));

    }
}
