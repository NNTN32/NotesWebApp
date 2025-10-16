package com.example.notesWeb.exception.redis;

import com.example.notesWeb.dtos.NoteDto.NoteRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NoteRedisProducer {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String sTREAM_kEY= "notes:create:stream";

    //Push message of request create Notes into Redis Stream
    public void sendNoteRequest(NoteRequest noteRequest, String username){
        Map<String, Object> fields = new HashMap<>();
        fields.put("content", noteRequest.getContent());
        fields.put("title", noteRequest.getTitle());
        fields.put("username", username);

        System.out.println("Sending request to Redis Stream: " + fields);
        redisTemplate.opsForStream()
                .add(StreamRecords.newRecord()
                        .in("notes:create:stream")
                        .ofMap(fields));
    }
}
