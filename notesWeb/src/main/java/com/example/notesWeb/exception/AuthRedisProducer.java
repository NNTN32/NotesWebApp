package com.example.notesWeb.exception;

import com.example.notesWeb.dtos.AuthRequest;
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
public class AuthRedisProducer {
    @Autowired
    private RedisTemplate<String , Object> redisTemplate;

    private static final String STREAM_KEY = "auth:login:stream";

    private static final String sTREAM_kEY= "notes:create:stream";

    public void sendLoginRequest(AuthRequest authRequest){
        //Using hashmap for key String & any value = object
        Map<String, Object> data = new HashMap<>();
        data.put("username", authRequest.getUsername());
        data.put("password", authRequest.getPassword());
        data.put("sessionId", authRequest.getSessionID());

        //Log check result
        System.out.println("Sending request to Redis Stream: " + data);
        redisTemplate.opsForStream()
                .add(StreamRecords.newRecord()
                        .in("auth:login:stream")
                        .ofMap(data));
    }

    //Push message of request create Notes into Redis Stream
    public void sendNoteRequest(NoteRequest noteRequest){
        Map<String, Object> fields = new HashMap<>();
        fields.put("content", noteRequest.getContent());
        fields.put("title", noteRequest.getTitle());

        System.out.println("Sending request to Redis Stream: " + fields);
        redisTemplate.opsForStream()
                .add(StreamRecords.newRecord()
                        .in("notes:create:stream")
                        .ofMap(fields));
    }
}
