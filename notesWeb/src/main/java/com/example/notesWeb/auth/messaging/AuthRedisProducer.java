package com.example.notesWeb.auth.messaging;

import com.example.notesWeb.auth.contract.AuthRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthRedisProducer {
    private final RedisTemplate<String , Object> redisTemplate;

    private static final String STREAM_KEY = "auth:login:stream";

    public void sendLoginRequest(AuthRequest authRequest){
        //Using hashmap for key String & any value = object
        Map<String, Object> data = new HashMap<>();
        data.put("username", authRequest.getUsername());
        data.put("password", authRequest.getPassword());
        data.put("sessionId", authRequest.getSessionID());

        //Log check result
        // Never log the authentication payload: it contains credentials.
        redisTemplate.opsForStream()
                .add(StreamRecords.newRecord()
                        .in(STREAM_KEY)
                        .ofMap(data));
    }

}