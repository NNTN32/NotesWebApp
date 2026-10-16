package com.example.notesWeb.notes.messaging;

import com.example.notesWeb.notes.contract.NoteRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NoteRedisProducer {
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String STREAM_KEY= "notes:create:stream";

    //Push message of request create Notes into Redis Stream
    public void sendNoteRequest(NoteRequest noteRequest, String username){
        Map<String, Object> fields = new HashMap<>();
        fields.put("content", noteRequest.getContent());
        fields.put("title", noteRequest.getTitle());
        fields.put("username", username);

        System.out.println("Sending request to Redis Stream: " + fields);
        redisTemplate.opsForStream()
                .add(StreamRecords.newRecord()
                        .in(STREAM_KEY)
                        .ofMap(fields));
    }
}
